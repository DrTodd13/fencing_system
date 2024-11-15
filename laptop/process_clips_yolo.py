import pandas as pd
import cv2
import os
import numpy as np
from PIL import Image
import torch
import sys
#import math
from scipy.signal import wiener
import multiprocessing as mp
from ultralytics import YOLO
import logging
import time
#import cProfile
#import pstats

os.environ['OPENCV_LOG_LEVEL'] = 'ERROR'
os.environ['GST_DEBUG'] = '0'

# colors used by the program
red = (255, 0, 0)
green = (0, 255, 0)
blue = (0, 0, 255)
colors = [red, green, blue]

def connect(frame, p1, p2, color):
    """
    Draw a line from p1 to p2 with the given color if p1 and p2 are both present.
    """
    thickness = 2  # Thickness of 2 px
    p1x, p1y = p1
    p2x, p2y = p2
    if p1x == 0 and p1y == 0:
        return
    if p2x == 0 and p2y == 0:
        return
    #if not isinstance(p1, np.ndarray) or not isinstance(p2, np.ndarray):
    #    print("got non-ndarray p1 or p2")
    cv2.line(frame, tuple(p1.astype(int)), tuple(p2.astype(int)), color, thickness)

kps_connections = [(5,7), (6,8), (7,9), (8,10), (5,6), (5,11), (6,12), (11,13), (12,14), (13,15), (14,16), (11,12)]
kps_names = ["Left shoulder/elbow", "Right shoulder/elbow", "Left elbow/wrist", "Right elbow/wrist", "Shoulders",
             "Left shoulder/hip", "Right shoulder/hip", "Left hip/knee", "Right hip/knee",
             "Left knee/ankle", "Right knee/ankle", "Hips"]

def drawLines(frame, kps, color):
    """
    Draw all the lines for a given fencer.
    """
    if isinstance(kps, torch.Tensor):
        kps = kps.numpy()
    for p0, p1 in kps_connections:
        connect(frame, kps[p0], kps[p1], color)

draw_lines = True

def update_avg_length(avg_len_list, fencer):
    for idx, pts in enumerate(kps_connections):
        p0, p1 = pts
        fp0 = fencer[p0]
        fp1 = fencer[p1]
        if np.any(fp0 == 0) or np.any(fp1 == 0):
            return
        avg_len_list[idx].append(point_dist(fp0, fp1))

def get_joint_length(fencer):
    return np.array([0 if np.any(p0 == 0) or np.any(p1 == 0) else point_dist(fencer[p0], fencer[p1]) for p0, p1 in kps_connections])
    
def leg_angle(player, knee, hip, ankle):
    """
    player is the x values of the pose estimation.
    knee, hip, ankle are the indices into the pose estimation.
    Returns:
        0 if the knee, hip, or ankle aren't present in the pose estimation
        1 if the leg is bent indicating the fencer is facing to the right
        -1 if the leg is bent indicating the fencer is facing to the left
    """
    if player[knee] == 0 or player[hip] == 0 or player[ankle] == 0:
        return 0
    return 1 if (player[knee] > (player[hip] + player[ankle])/2) else -1

"""
def hypot(a,b):
    x1, y1 = a
    x2, y2 = b
    distance = math.sqrt((x2 - x1)**2 + (y2 - y1)**2)
    return distance
"""

def similarity(p0, p1, width, height):
    num_ratios = 0
    ratio_total = 0
    
    for c1, c2 in kps_connections:
        if (p0[c1] != 0).all() and (p0[c2] != 0).all() and (p1[c1] != 0).all() and (p1[c2] != 0).all():
            num_ratios += 1
            ratio_total += 1 * (point_dist(p0[c1], p0[c2]) / point_dist(p1[c1], p1[c2]))
    for x in range(5,17):
        if (p0[x] != 0).all() and (p1[x] != 0).all():
            num_ratios += 1
            #ratio_total += 1000*(abs(p0[x,1] - p1[x,1]) / p0[x,1])
            ratio_total += (1200 * (abs(p0[x,1] - p1[x,1]) / height)) + ((1.0 / (3 * (abs(p0[x,0] - p1[x,0]) / width))) if (p0[x,0] - p1[x,0] != 0) else np.inf)
            #ratio_total += 1000*(abs(p0[x,1] - p1[x,1]) / p0[x,1]) * ((1.0 / (3 * (abs(p0[x,0] - p1[x,0]) / width))) if (p0[x,0] - p1[x,0] != 0) else np.inf)
    if num_ratios == 0:
        return np.inf
    return (ratio_total / num_ratios)

def filter_zero(x):
    return x[x != 0]

def average_pixel_move(a, b, use_abs=True):
    # Mask elements where either array has zero
    mask = (a != 0) & (b != 0)

    # Calculate differences
    differences = a - b
    if use_abs:
        differences = np.abs(differences)

    # Apply mask to get valid differences
    valid_differences_x = differences[mask[:, 0], 0]
    valid_differences_y = differences[mask[:, 1], 1]
    assert len(valid_differences_x) == len(valid_differences_y)

    if len(valid_differences_x) == 0:
        return np.abs(np.mean(filter_zero(a)) - np.mean(filter_zero(b)))
    else:
        # Calculate average difference
        return np.mean(valid_differences_x) + 2 * np.mean(valid_differences_y)

def apply_flow(fencer, cur_frame_num, flow):
    frames_since_good = cur_frame_num - fencer[1]
    good_mask = np.all(fencer[0] == 0, axis=1)
    masked = np.ma.array(fencer[0], mask=np.tile(good_mask[:, None], fencer[0].shape[1]))
    sum_flow = np.sum(flow[-frames_since_good:], axis=0)
    return (masked + sum_flow).filled(fencer[0]), frames_since_good
    
def apply_flow_one(fencer, sum_flow):
    good_mask = np.all(fencer == 0, axis=1)
    masked = np.ma.array(fencer, mask=np.tile(good_mask[:, None], fencer.shape[1]))
    return (masked + sum_flow).filled(fencer)
    
def find_left_right(possible_fencers, lastgood, simmax, cur_frame_num, output, flow):
    left_good, frames_since_good_left = apply_flow(lastgood[0], cur_frame_num, flow)
    right_good, frames_since_good_right = apply_flow(lastgood[1], cur_frame_num, flow)
    
    left_diffs = [average_pixel_move(possible_fencers[i], left_good) for i in range(len(possible_fencers))]
    right_diffs = [average_pixel_move(possible_fencers[i], right_good) for i in range(len(possible_fencers))]
    
    def find_best(diff, frame_simmax):
        found = None
        best = frame_simmax
        lowest = np.inf
        for i in range(len(diff)):
            if diff[i] < lowest:
                lowest = diff[i]
            if diff[i] < best:
                found = i
                best = diff[i]
        print("flr:", best, found, lowest, end=" ", file=output)
        return found, best
    
    frame_simmax_left = simmax * (2 - 0.5**frames_since_good_left)
    frame_simmax_right = simmax * (2 - 0.5**frames_since_good_right)
    left_index, left_best = find_best(left_diffs, frame_simmax_left)
    right_index, right_best = find_best(right_diffs, frame_simmax_right)
    if left_index is not None and right_index is not None and left_index == right_index:
        if left_best < right_best:
            right_diffs[right_index] = np.inf
            right_index, right_best = find_best(right_diffs, frame_simmax_right)
        else:
            left_diffs[left_index] = np.inf
            left_index, left_best = find_best(left_diffs, frame_simmax_left)
            
    return left_index, left_best / frames_since_good_left, right_index, right_best / frames_since_good_right

pm_total = 0

def update_point_movements(prev_frame, cur_frame, point_movements, pm_stats, output, flows, fidx):
    global pm_total
    if pm_total >= 100 or pm_stats is not None:
        return pm_stats
    prev_frame = apply_flow_one(prev_frame, flows[fidx])
    mask = (prev_frame[:,0] != 0) & (prev_frame[:,1] != 0) & (cur_frame[:,0] != 0) & (cur_frame[:,1] != 0)
    dist = np.sqrt(np.square(cur_frame - prev_frame).sum(axis=1))
    point_movements[pm_total, mask] = dist[mask]
    pm_total += 1
    if pm_total == 100:
        def get_point_stats(i, point_movements):
            col = point_movements[point_movements[:,i] != 0, i] 
            if len(col) == 0:
                return (0,0)
            z_score = (col - col.mean()) / col.std()
            threshold = 3
            # Filter out the outliers
            col_no_outliers = col[np.abs(z_score) < threshold]
            cno_mean = col_no_outliers.mean()
            cno_max = col_no_outliers.max()
            print(f"get_point_stats {i} {cno_mean} {cno_max} {len(col)} {len(col_no_outliers)}", file=output)
            return (cno_mean, cno_max)

        pm_stats = [get_point_stats(i, point_movements) for i in range(17)]   
        return pm_stats
    return None

def point_dist(p0, p1):
    return np.sqrt(np.sum(np.square(p0-p1)))
    
def deblur(frame):
    # Split the frame into its color channels
    frame = frame.astype('float64')
    b, g, r = cv2.split(frame)
    
    # Apply the Wiener filter to each channel
    deblurred_b = wiener(b, mysize=(5, 5))
    deblurred_g = wiener(g, mysize=(5, 5))
    deblurred_r = wiener(r, mysize=(5, 5))
    
    # Merge the channels back together
    deblurred_frame = cv2.merge((deblurred_b, deblurred_g, deblurred_r))
    
    # Convert to uint8
    return np.clip(deblurred_frame, 0, 255).astype('uint8')
    #return np.uint8(deblurred_frame)

def opposite_side_screen(player0, player1, width):
    p0x = player0[:,0]
    p1x = player1[:,0]
    p0mean = np.mean(filter_zero(p0x))
    p1mean = np.mean(filter_zero(p1x))
    midpoint = width/2
    if p0mean < midpoint and p1mean < midpoint:
        return (midpoint - p1mean) / 2
    elif p0mean >= midpoint and p1mean >= midpoint:
        return (p0mean - midpoint) / 2
    return 0

def outside_box(player0, player1, box):
    p0y = player0[:,1]
    p1y = player1[:,1]
    p0min = np.min(filter_zero(p0y))
    p0max = np.max(filter_zero(p0y))
    p1min = np.min(filter_zero(p1y))
    p1max = np.max(filter_zero(p1y))
    if p0min < box[0] or p1min < box[0]:
        return np.inf
    elif p0max > box[1] or p1max > box[1]:
        return np.inf
    else:
        box_avg = box[1]
        missing_lower_penalty = np.all(p0y[13:] == 0) or np.all(p1y[13:] == 0)
        missing_upper_penalty = np.all(p0y[0:11] == 0) or np.all(p1y[0:11] == 0)
        missing_one_side_penalty = np.all(p0y[::2] == 0) or np.all(p0y[1::2] == 0) or np.all(p1y[::2] == 0) or np.all(p1y[1::2] == 0)
        return 3 * (abs(np.mean(filter_zero(p0y) - box_avg)) + abs(np.mean(filter_zero(p1y) - box_avg))) + (1000 if missing_lower_penalty else 0) + (1000 if missing_one_side_penalty else 0) + (1000 if missing_upper_penalty else 0)

def interpolate(frame_keypoints, pm_stats, flows):
    """ Interpolate missing data.  Try to fix yolo mistakes.
    """
    left_last_good = [(-1, -1) for i in range(17)]
    right_last_good = [(-1, -1) for i in range(17)]
    # Interpolate missing data
    
    # First get the length from each joint-to-joint.
    left_avg_len = [[] for _ in range(len(kps_connections))]
    right_avg_len = [[] for _ in range(len(kps_connections))]
    for fidx in range(len(frame_keypoints)):
        update_avg_length(left_avg_len, frame_keypoints[fidx][0])
        update_avg_length(right_avg_len, frame_keypoints[fidx][1])

    def gen_bl_stats(part_list):
        col = np.array(part_list)
        z_score = (col - col.mean()) / col.std()
        threshold = 3
        # Filter out the outliers
        col_no_outliers = col[np.abs(z_score) < threshold]
        #print("num outliers = ", len(col) - len(col_no_outliers))
        cno_mean = col_no_outliers.mean()
        cno_max = col_no_outliers.max()
        cno_std = col_no_outliers.std()
        return (cno_mean, cno_std, cno_max)

    left_stats = None
    right_stats = None
    
    #left_stats = np.array([gen_bl_stats(x) for x in left_avg_len])
    #right_stats = np.array([gen_bl_stats(x) for x in right_avg_len])
        
    #print("left_stats", left_stats)
    #print("right_stats", right_stats)
    
    def get_anomaly_indices(stats, joint_lengths):
        means = stats[:, 0]
        stddevs = stats[:, 2]
        # Calculate the condition for being more than 3 standard deviations away from the mean
        condition = np.logical_and(np.abs(joint_lengths - means) > 3 * stddevs, joint_lengths != 0)
        #print("gai", joint_lengths, means, stddevs, np.abs(joint_lengths - means), np.abs(joint_lengths - means) > 3 * stddevs)
        # Get the indices where the condition is True
        indices = np.where(condition)[0]
        return indices
        
    for fidx in range(len(frame_keypoints)):
        if fidx >= 82:
            pass

        #print("interpolating", fidx)
        def update(last_good, frame_keypoints, fkidx, fidx, fencer_stats):
            curpose = frame_keypoints[fidx][fkidx]
            
            #joint_lengths = np.array(get_joint_length(curpose))
            #anomalous_indices = get_anomaly_indices(fencer_stats, joint_lengths)
            #if len(anomalous_indices) > 0:
            #    print("anomalous_indices", fkidx, fidx, ",".join([kps_names[i] for i in anomalous_indices]))
            
            prevpose = apply_flow_one(frame_keypoints[fidx-1][fkidx], flows[fidx])
            #if fidx >= 82 and fidx <= 84 and fkidx == 0:
            #    print(f"update {fidx} {curpose}")
            for i in range(5, 17):
                # Got a good point in this frame.
                if (curpose[i] != 0).all():
                    # Oops...not so fast...the point is an outlier so zero it.
                    if fidx > 0:
                        if (prevpose[i] != 0).all():
                            if np.sqrt(np.sum(np.square(curpose[i]-prevpose[i]))) > pm_stats[i][1] * 2:
                                curpose[i] = 0
                                continue
                    # Last good data from previous frame so nothing to do.
                    if last_good[i][1] != fidx - 1:
                        # Some bad frames
                        if last_good[i][1] == -1:
                            pass
                            #for fix_frame in range(fidx):
                            #    #print("fixing", fix_frame, fkidx, i, frame_keypoints[fix_frame][fkidx][i], frame_keypoints[fidx][fkidx][i])
                            #    frame_keypoints[fix_frame][fkidx][i] = frame_keypoints[fidx][fkidx][i]
                        else:
                            missing_frames = fidx - last_good[i][1] - 1
                            frame_diff = (curpose[i] - last_good[i][0]) / missing_frames
                            for fix_frame in range(last_good[i][1] + 1, fidx):
                                #print("fix fd", fix_frame, fkidx, i, frame_keypoints[fix_frame][fkidx][i], frame_keypoints[fix_frame-1][fkidx][i] + frame_diff)
                                frame_keypoints[fix_frame][fkidx][i] = frame_keypoints[fix_frame-1][fkidx][i] + frame_diff
                    last_good[i] = (curpose[i], fidx)
        
        update(left_last_good, frame_keypoints, 0, fidx, left_stats)
        update(right_last_good, frame_keypoints, 1, fidx, right_stats)
    
    same_value = 0
    use_zero = 1
    use_average = 2
    end_mode = use_zero
    
    def handle_end(last_good, frame_keypoints, fkidx):
        for i in range(5, 17):
            if last_good[i][1] != len(frame_keypoints) - 1:
                # This approach is terrible!  Need to fix.
                for fix_frame in range(last_good[i][1] + 1, len(frame_keypoints)):
                    if end_mode == same_value:
                        frame_keypoints[fix_frame][fkidx][i] = frame_keypoints[last_good[i][1]][fkidx][i]
                    elif end_mode == use_zero:
                        frame_keypoints[fix_frame][fkidx][i] = 0
                    elif end_mode == use_average:
                        frame_keypoints[fix_frame][fkidx][i] = frame_keypoints[fix_frame-1][fkidx][i] + average_pixel_move(frame_keypoints[fix_frame][fkidx], frame_keypoints[fix_frame-1][fkidx], use_abs=False)
            
    handle_end(left_last_good, frame_keypoints, 0)
    handle_end(right_last_good, frame_keypoints, 1)

"""
    # Select good points
    good_new = p1[st == 1]
    good_old = p0[st == 1]

    # Draw the tracks
    for i, (new, old) in enumerate(zip(good_new, good_old)):
        a, b = new.ravel()
        c, d = old.ravel()
        mask = cv2.line(mask, (a, b), (c, d), color[i].tolist(), 2)
        frame = cv2.circle(frame, (a, b), 5, color[i].tolist(), -1)
"""

def vertical_parts(a, b, avg_torso_width):
            # 5: Left Shoulder 6: Right Shoulder 
            # 7: Left Elbow 8: Right Elbow 9: Left Wrist 10: Right Wrist 
            # 11: Left Hip 12: Right Hip 13: Left Knee 14: Right Knee 
    penalty = 0
    if np.all(a[5:7] != 0):
        penalty += abs(abs(a[5,0] - a[6,0]) - avg_torso_width) * 20
    if np.all(b[5:7] != 0):
        penalty += abs(abs(b[5,0] - b[6,0]) - avg_torso_width) * 20
    if np.all(a[11:13] != 0):
        penalty += abs(abs(a[11,0] - a[12,0]) - avg_torso_width) * 20
    if np.all(b[11:13] != 0):
        penalty += abs(abs(b[11,0] - b[12,0]) - avg_torso_width) * 20
    return penalty
   
def process(arg_data):
    global yolo_model
    start_frame, touch_frame, mode, pm_stats, fdir, prefix = arg_data
    logging.info(f'Starting frame {touch_frame} {pm_stats is None}')
    # Open the mp4 file with OpenCV
    cap = cv2.VideoCapture(f"{fdir}/{prefix}_touch{touch_frame}.mp4")
    #fps = int(cap.get(cv2.CAP_PROP_FPS))
    fps_double = cap.get(cv2.CAP_PROP_FPS)
    # Get the width and height of the video
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    #width_double = cap.get(cv2.CAP_PROP_FRAME_WIDTH)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    #height_double = cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    if mode == 1:
        output = sys.stdout
    else:
        output = open(f"{fdir}/{prefix}_touch_frame_debug_{touch_frame}.txt", "w")

    if prefix == "Shanghai_Yellow":
        fencer_box = (150, 315)
        avg_torso_width = 20
    elif prefix == "Shanghai_Red":
        fencer_box = (110, 340)
        avg_torso_width = 25
    else:
        print("Don't know fencer_box for this video.")
        return (touch_frame, -3)

    # Check if the video file opened successfully
    if not cap.isOpened():
        print(f"Error opening video file {prefix}_touch{touch_frame}.mp4", file=output)
        return (touch_frame, -1)

    # Parameters for ShiTomasi corner detection
    feature_params = dict(maxCorners=100, qualityLevel=0.3, minDistance=7, blockSize=7)
    
    # Parameters for Lucas-Kanade optical flow
    lk_params = dict(winSize=(15, 15), maxLevel=1, criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 10, 0.03), minEigThreshold=1e-3)

    # Read frames from start_frame to touch_frame
    frames = []
    orig_frames = []
    print("Capturing frame", end = " ", file=output)
    old_gray_frame = None
    flows = []
    for _ in range(touch_frame - start_frame):
        ret, frame = cap.read()
        if not ret:
            print("Error reading frame", file=output)
            break

        orig_frames.append(frame)
        print(f"{len(frames)}", end = " ", file=output)
        frames.append(deblur(frame))
        
        if old_gray_frame is None:
            old_gray_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            p0 = cv2.goodFeaturesToTrack(old_gray_frame, mask=None, **feature_params)
            flows.append(np.ones(2))
        else:
            frame_gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
            # Calculate optical flow
            p1, st, err = cv2.calcOpticalFlowPyrLK(old_gray_frame, frame_gray, p0, None, **lk_params)
        
            # Select good points
            good_new = p1[st == 1]
            good_old = p0[st == 1]
            good_diff = good_new - good_old
            flow = np.mean(good_diff, axis=0)
            flows.append(flow)
            #print(f"flow {flow}", end = " ", file=output)

            old_gray_frame = frame_gray.copy()
            p0 = good_new.reshape(-1, 1, 2)

    print("", file=output)

    frame_keypoints = []
    lastgood = None
    good_similarity_total = 0
    good_similarity_count = 0
    max_good_sim = 0
    min_good_sim = np.inf
    total_good_sim = 0
    count_good_sim = 0
    last_good_fidx = -1

    position = (50, 50)
    font = cv2.FONT_HERSHEY_SIMPLEX
    font_scale = 1
    thickness = 2

    point_movements = np.zeros((100,17))
    for fidx, frame in enumerate(frames):
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        pil_image = Image.fromarray(rgb_frame)
        if yolo_lock is None:
            results = yolo_model.predict(pil_image, conf=0.05, verbose=False)
        else:
            with yolo_lock:
                results = yolo_model.predict(pil_image, conf=0.05, verbose=False)
        
        #simmax = 20 if good_similarity_count == 0 else (good_similarity_total / good_similarity_count) * 1.5
        #simmax = max(40, 40 if count_good_sim == 0 else (total_good_sim / count_good_sim) * 5)
        simmax = 50
        if fidx >= 82:
            pass
        
        for result in results:
            # 0: Nose 1: Left Eye 2: Right Eye 3: Left Ear 4: Right Ear 
            # 5: Left Shoulder 6: Right Shoulder 
            # 7: Left Elbow 8: Right Elbow 9: Left Wrist 10: Right Wrist 
            # 11: Left Hip 12: Right Hip 13: Left Knee 14: Right Knee 
            # 15: Left Ankle 16: Right Ankle
            print("# of poses:", len(result.keypoints), fidx, 25 if count_good_sim == 0 else (total_good_sim / count_good_sim), end=" ", file=output)
            possible_fencers = [result.keypoints.xy[objnum].numpy() for objnum in range(len(result.keypoints))]
            # Ignore what the pose estimator thinks are nose, eyes, and ears.
            for i in range(len(possible_fencers)):
                possible_fencers[i][0:5] = 0
                # Identify rows with any zeros
                rows_with_zeros = np.any(possible_fencers[i] == 0, axis=1)
                # Zero out those rows
                possible_fencers[i][rows_with_zeros] = 0
                
                # If only shoulders are kwown then zero everything because the model makes a lot of mistakes thinking shoulders are hips or knees.
                if np.all(possible_fencers[i][:5] == 0) and np.all(possible_fencers[i][7:] == 0) and np.all(possible_fencers[i][5:7] != 0):
                    possible_fencers[i][:] = 0
                
            # Filter fencers for which there are no points after removing head points.
            possible_fencers = list(filter(lambda x: not (x == 0).all(), possible_fencers))
            if lastgood is None:
                print(possible_fencers, file=output)
                if len(possible_fencers) < 2:
                    print("Less than 2 people detected in first frame.", file=output)
                    return (touch_frame, -2)
                elif len(possible_fencers) > 2:
                    total = len(possible_fencers)
                    simmatrix = np.full((total, total), np.inf)
                    for i in range(total):
                        for j in range(i+1, total):
                            simmatrix[i,j] = (similarity(possible_fencers[i], possible_fencers[j], width, height) + 
                                              opposite_side_screen(possible_fencers[i], possible_fencers[j], width) + 
                                              outside_box(possible_fencers[i], possible_fencers[j], fencer_box) + 
                                              vertical_parts(possible_fencers[i], possible_fencers[j], avg_torso_width))
                            simmatrix[j,i] = simmatrix[i,j]
                    print("simmatrix\n", simmatrix, file=output)
                    min_value_indices = np.unravel_index(np.argmin(simmatrix), simmatrix.shape)
                    twofencers = [possible_fencers[min_value_indices[0]], possible_fencers[min_value_indices[1]]]
                    good_similarity_total += simmatrix[min_value_indices]
                    good_similarity_count += 1
                else:    
                    twofencers = [possible_fencers[0], possible_fencers[1]]
                    min_value_indices = (0, 1)
                
                torso = [5, 6, 11, 12]
                player0x = twofencers[0][:,0]
                player1x = twofencers[1][:,0]
                player0average = np.mean(filter_zero(player0x))
                player1average = np.mean(filter_zero(player1x))
                torso0 = player0x[torso]
                torso1 = player1x[torso]
                fz_torso0 = filter_zero(torso0)
                fz_torso1 = filter_zero(torso1)
                player0minimum = np.min(fz_torso0) if len(fz_torso0) > 0 else -1
                player1minimum = np.min(fz_torso1) if len(fz_torso1) > 0 else -1
                player0left = leg_angle(player0x, 13, 11, 15)
                player1left = leg_angle(player1x, 13, 11, 15)
                player0right = leg_angle(player0x, 14, 12, 16)
                player1right = leg_angle(player1x, 14, 12, 16)
                
                leftright = player0average < player1average
                print(leftright, player0x, player1x, player0average, player1average, "torso", torso0, torso1, player0minimum, player1minimum, player0left, player0right, player1left, player1right, file=output)
                if (player0minimum < player1minimum) != leftright:
                    print("torso minimum contradicts overall average", leftright, player0minimum, player1minimum, file=output)
                if (player0left != 0) and ((player0left != -1) != leftright):
                    print("torso minimum contradicts player0left", leftright, player0left, file=output)
                if (player0right != 0) and ((player0right != -1) != leftright):
                    print("torso minimum contradicts player0right", leftright, player0right, file=output)
                if (player1left != 0) and ((player1left != 1) != leftright):
                    print("torso minimum contradicts player1left", leftright, player1left, file=output)
                if (player1right != 0) and ((player1right != 1) != leftright):
                    print("torso minimum contradicts player1right", leftright, player1right, file=output)
                    
                # Swap so that left fencer is index 0 and right fencer is index 1, if necessary.
                if not leftright:
                    twofencers[0], twofencers[1] = twofencers[1], twofencers[0]
    
                other_keypoints = []                
                for objnum in range(len(possible_fencers)):
                    if objnum in min_value_indices:
                        continue
                    kps = possible_fencers[objnum]
                    other_keypoints.append(kps)
                frame_keypoints.append((twofencers[0], twofencers[1], other_keypoints))
                lastgood = ((twofencers[0],fidx), (twofencers[1],fidx))
                last_good_sim = similarity(twofencers[0], twofencers[1], width, height)
            else:
                left_index, left_best, right_index, right_best = find_left_right(possible_fencers, lastgood, simmax, fidx, output, flows)
                assert (left_index != right_index or left_index is None)
                print("pre", left_index, right_index, end=" ", file=output)
                def update_stats(index, best):
                    if index is None:
                        return
                    nonlocal max_good_sim, min_good_sim, total_good_sim, count_good_sim
                    max_good_sim = max(best, max_good_sim)
                    min_good_sim = min(best, min_good_sim)
                    total_good_sim += best
                    count_good_sim += 1
                    
                update_stats(left_index, left_best);
                update_stats(right_index, right_best)
                    
                if left_index is None or right_index is None:
                    if False:
                        # Found one but not the other
                        if left_index is not None or right_index is not None:
                            good_index = left_index if left_index is not None else right_index
                            simlist = [np.inf if i==good_index else similarity(possible_fencers[i], possible_fencers[good_index], width, height) for i in range(len(possible_fencers))]
                            
                            # Mask the array to ignore values below the minimum similarity seen so far
                            masked_simlist = np.ma.masked_less_equal(simlist, min_good_sim)
    
                            # Find the index of the minimum value above the threshold
                            most_similar_index = np.argmin(masked_simlist)
    
                            #most_similar_index = np.argmin(np.array(simlist))
                            print("msi", most_similar_index, simlist, simlist[most_similar_index], last_good_sim, fidx - last_good_fidx, end=" ", file=output)
                            if simlist[most_similar_index] < last_good_sim * (0.5 * (fidx - last_good_fidx)):
                            #if simlist[most_similar_index] < max_good_sim * 1:
                            #if simlist[most_similar_index] < simmax:
                                if left_index is None:
                                    left_index = most_similar_index
                                if right_index is None:
                                    right_index = most_similar_index
                        else:
                            pass
                            #assert False
                else:
                    good_similarity = similarity(possible_fencers[left_index], possible_fencers[right_index], width, height)
                    print("gs", good_similarity, end=" ", file=output)
                    good_similarity_total += good_similarity
                    #max_good_sim = max(good_similarity, max_good_sim)
                    #min_good_sim = min(good_similarity, min_good_sim)
                    last_good_sim = good_similarity
                    last_good_fidx = fidx
                    good_similarity_count += 1

                print("found indices", left_index, right_index, fidx, file=output)
                # Keep last good if not found.  Update if found.
                lastgood = (lastgood[0] if left_index is None else (possible_fencers[left_index], fidx), lastgood[1] if right_index is None else (possible_fencers[right_index], fidx))
                other_keypoints = []                
                for objnum in range(len(possible_fencers)):
                    if objnum == left_index or objnum == right_index:
                        continue
                    kps = possible_fencers[objnum]
                    other_keypoints.append(kps)
                frame_keypoints.append((np.zeros((17,2)) if left_index is None else possible_fencers[left_index],
                                        np.zeros((17,2)) if right_index is None else possible_fencers[right_index], 
                                        other_keypoints))
                if pm_total < 100 and len(frame_keypoints) > 1:
                    if not (frame_keypoints[-1][0] == 0).all() and not (frame_keypoints[-2][0] == 0).all():
                        pm_stats = update_point_movements(frame_keypoints[-2][0], frame_keypoints[-1][0], point_movements, pm_stats, output, flows, fidx)
                    if not (frame_keypoints[-1][1] == 0).all() and not (frame_keypoints[-2][1] == 0).all():
                        pm_stats = update_point_movements(frame_keypoints[-2][1], frame_keypoints[-1][1], point_movements, pm_stats, output, flows, fidx)
                        
    assert len(frames) == len(frame_keypoints)

    interpolate(frame_keypoints, pm_stats, flows)
            
    processed_frames = []
    for fidx, (frame, keypoints) in enumerate(zip(orig_frames, frame_keypoints)):
        # Write the number on the frame
        cv2.putText(frame, f"{touch_frame} {fidx}", position, font, font_scale, red, thickness)

        for kps in keypoints[2]:
            if draw_lines:
                drawLines(frame, kps, colors[0])
            else:
                for kp in kps:
                    x, y = kp
                    cv2.circle(frame, (int(x), int(y)), 5, (0, 255, 0), -1)
        if keypoints[0] is not None:
            drawLines(frame, keypoints[0], colors[2])
        if keypoints[1] is not None:
            drawLines(frame, keypoints[1], colors[1])
        processed_frames.append(frame)
            
    # Release the video capture object
    cap.release()
    
    logging.info(f'Finished frame {touch_frame}')
    return (fps_double, width, height, processed_frames, pm_stats, frame_keypoints)

yolo_model = None
yolo_lock = None

def init_worker(yolo_file):
    global yolo_model
    logging.info("Before yolo model")
    # Initialize YOLO model once per thread
    yolo_model = YOLO(yolo_file)
    #yolo_model.export(format='onnx')
    #yolo_model = YOLO("yolo11x-pose.onnx")
    logging.info("After yolo model")
    global yolo_lock
    yolo_lock = mp.Lock()

def main(prefix):
    fdir = prefix
    prefix = os.path.basename(fdir)
    
    # Read the CSV file into a pandas dataframe
    """['start_frame', 
        'touch_frame', 
        'time',
        'prev_left', 
        'prev_right', 
        'next_left', 
        'next_right', 
        'two_left', 
        'two_right', 
        'right_of_way']
    """
    df = pd.read_csv(fdir + "/" + prefix + '_touch_score.csv')
    
    # Path to yolo model on disk
    dir_path = os.path.dirname(os.path.realpath(__file__))
    yolo_file = dir_path + '\\yolo11x-pose.onnx'
    #yolo_file = dir_path + '\\yolo11x-pose.pt'
    init_worker(yolo_file)
    #yolo_model.to('')  # rocm eventually?
    #print("Initialized yolo model")

    first_processed = True
    pm_stats = None
    
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    
    write_all_touch_file = True
    all_touch_path = None
    
    if write_all_touch_file:
        tasks = []
    
    logging.basicConfig(level=logging.INFO)
    right_of_way_column = df[df['right_of_way'] != 0]['right_of_way'].to_numpy()
    count = right_of_way_column.shape[0]
    
    # Iterate through the dataframe
    for index, row in df.iterrows():
        start_frame = row['start_frame']
        touch_frame = row['touch_frame']
        right_of_way = row['right_of_way']
        if right_of_way == 0:
            continue
    
        print(f"Frame {touch_frame} {index}")
        # Shanghai_Yellow 51 - 0 touches, 159 good clips
        # Shanghai_Red - 48395 (red and green on top of each other)
        if not write_all_touch_file and touch_frame != 403160:
            continue
    
        if write_all_touch_file:
            if first_processed:
                first_processed = False
                start_time = time.time()
                process_res = process((start_frame, touch_frame, 0, pm_stats, fdir, prefix))
                assert isinstance(process_res, tuple)
                if len(process_res) == 2:
                    print(f"Problem with processing first frame {process_res[0]} with code {process_res[1]}")
                    sys.exit(-1)
                fps_double, width, height, processed_frames, pm_stats, keypoints = process_res
                end_time = time.time()
                one_iter_time = end_time - start_time
            else:
                tasks.append((start_frame, touch_frame, 0, pm_stats, fdir, prefix))
        else:
            process_res = process((start_frame, touch_frame, 1, pm_stats, fdir, prefix))
            assert isinstance(process_res, tuple)
            if len(process_res) == 2:
                print(f"Problem with processing frame {process_res[0]} with code {process_res[1]}")
                sys.exit(-1)
            fps_double, width, height, processed_frames, pm_stats, keypoints = process_res
            
            for frame in processed_frames:
                cv2.imshow('Buffered Frame', frame)
                cv2.waitKey(0)
    
            # Play the buffer frames
            keep_going = not write_all_touch_file
            while keep_going:
                #keep_going = False
                for frame in processed_frames:
                    cv2.imshow('Buffered Frame', frame)
                    if cv2.waitKey(25) & 0xFF == ord('q'):
                        keep_going = False
                        break
        
            # Close the display window
            cv2.destroyAllWindows()
        
    if write_all_touch_file:
        training_data = np.empty((count, len(processed_frames), 48))
        
        use_procs = 22
        print(f"Running time estimate is {(len(tasks) / use_procs) * one_iter_time} seconds.")
        pool_start = time.time()
        logging.info("Starting the pool")
        with mp.Pool(processes=use_procs, initializer=init_worker, initargs=(yolo_file,)) as pool:
            logging.info("Starting the map")
            results = pool.map(process, tasks)
            logging.info("Map done")
        pool_end = time.time()
        print(f"Actual running time was {pool_end - pool_start} seconds.")
        assert all_touch_path is None
        all_touch_path = fdir + "/" + prefix + "_all_touches.mp4"
        all_touches = cv2.VideoWriter(all_touch_path, fourcc, fps_double, (width, height))
        for frame in processed_frames:
            all_touches.write(frame)
            
        def fill_in_training(sample_num, keypoints):
            for i in range(len(keypoints)):
                training_data[sample_num, i, :] = np.concatenate((keypoints[i][0][5:,:].flatten(), keypoints[i][1][5:,:].flatten()))
                
        sample_num = 0
        fill_in_training(sample_num, keypoints)
        sample_num += 1
        bad_frames = []
        for ridx, result in enumerate(results):
            assert isinstance(result, tuple)
            if len(result) == 2:
                print(f"Problem with processing frame {result[0]} with code {result[1]}")
                bad_frames.append(ridx)
                continue
            _, _, _, processed_frames, _, keypoints = result
            for frame in processed_frames:
                all_touches.write(frame)
            fill_in_training(sample_num, keypoints)
            sample_num += 1
                
        all_touches.release()

        bad_frames = np.array(bad_frames) + 1
        if len(bad_frames) > 0:
            training_data = training_data[:-len(bad_frames)]
            right_of_way_column = np.delete(right_of_way_column, bad_frames, axis=0)
        np.save(fdir + "/" + prefix + '_training.npy', training_data)
        np.save(fdir + "/" + prefix + '_ground_truth.npy', right_of_way_column)
        
        video_size = np.array([width, height])
        np.save(fdir + "/" + prefix + '_video_size.npy', video_size)
        # Load from a .npy file 
        #loaded_array = np.load('array.npy')
    
if __name__ == "__main__":
    prefix = "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red"
    name = os.path.basename(prefix) 
    #cProfile.run('main(prefix)', 'profile_output')
    #p = pstats.Stats('profile_output')
    #p.strip_dirs().sort_stats('time').print_stats(100)
    main(prefix)
