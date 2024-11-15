"""
Create frame_data.csv which has the frame number at which an
interesting touch is recorded.  An interesting touch is one needed
referee input but both players' lights came on and both of them
weren't white.
"""
import cv2
import numpy as np
import pandas as pd
import math
import os
import single_step
import sys

# Get the frame rate of the video
def get_fps(cap):
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    cap.set(cv2.CAP_PROP_POS_FRAMES, total_frames-1)
    #cap.set(cv2.CAP_PROP_POS_AVI_RATIO, 1)
    ret, _ = cap.read()
    if not ret:
        assert False
    # Get the duration of the video in milliseconds
    duration_ms = cap.get(cv2.CAP_PROP_POS_MSEC)
    # Convert duration to seconds
    duration_seconds = duration_ms / 1000.0
    # Calculate the exact frame rate
    fps = total_frames / duration_seconds
    return fps

# Define a function to compare images
def images_are_similar(img1, img2, threshold=-8.0):
    if img1.shape != img2.shape:
        return False
    difference = cv2.absdiff(img1, img2)
    similarity = 1 - np.sum(difference) / np.prod(img1.shape)
    return similarity >= threshold

def get_min_sec(frame, fps):
    frame_minutes = math.floor(frame / (fps*60))
    frame_seconds = math.floor((frame - int(frame_minutes * (fps*60))) / fps)
    return frame_minutes, frame_seconds

def main(prefix):    
    # Load reference images
    left_white = cv2.imread('left_white.jpg')
    left_red = cv2.imread('left_red.jpg')
    right_white = cv2.imread('right_white.jpg')
    right_green = cv2.imread('right_green.jpg')
    
    fdir = prefix
    prefix = os.path.basename(fdir)
    # Load the video file
    video_path = fdir + "/" + prefix + '.mp4'
    cap = cv2.VideoCapture(video_path)
    
    # Check if the video opened successfully
    if not cap.isOpened():
        print("Error: Could not open video.")
        sys.exit()
    
    fps = cap.get(cv2.CAP_PROP_FPS)
    #fps = get_fps(cap)
    print("fps:", fps)
      
    sscap = single_step.single_step(cap)
    
    # Create a DataFrame to store the results
    df = pd.DataFrame(columns=['start_frame', 
                               'touch_frame',
                               'time',
                               'left_white', 
                               'right_white', 
                               'red', 
                               'green',
                               'refereed'])
    
    # Convert coordinates to tuples
    left_white_coords = ((200, 299), (250, 301))
    left_red_coords = ((200, 328), (250, 330))
    right_white_coords = ((400, 299), (450, 301))
    right_green_coords = ((400, 328), (450, 330))
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    processed_frames = 0

    debug = False
    
    if debug:
        dfile = open(prefix + "_extract_touch_debug.txt", "w")
        
    #found_file = open("found.txt", "w")
    
    last_found = 0
    found_light = None
    for frame_num in range(0, frame_count, 5):
        # Set the video to the specific frame
        sscap.set(frame_num)
    
        # Read the frame
        ret, frame = sscap.read()
        if not ret:
            break
    
        if frame_num > 5075:
            pass
        
        # Extract the rectangles
        lw_rect = frame[left_white_coords[0][1]:left_white_coords[1][1], left_white_coords[0][0]:left_white_coords[1][0]]
        lr_rect = frame[left_red_coords[0][1]:left_red_coords[1][1], left_red_coords[0][0]:left_red_coords[1][0]]
        rw_rect = frame[right_white_coords[0][1]:right_white_coords[1][1], right_white_coords[0][0]:right_white_coords[1][0]]
        rr_rect = frame[right_green_coords[0][1]:right_green_coords[1][1], right_green_coords[0][0]:right_green_coords[1][0]]
    
        left_white_light = images_are_similar(lw_rect, left_white)
        right_white_light = images_are_similar(rw_rect, right_white)
        red_light = images_are_similar(lr_rect, left_red)
        green_light = images_are_similar(rr_rect, right_green)
        any_light = left_white_light or right_white_light or red_light or green_light
        
        if debug and any_light:
            sec_count = frame_num // fps
            print(frame_num, f"{sec_count//60}:{sec_count%60}", left_white_light, red_light, right_white_light, green_light, file=dfile)
            
        time_expired = False
        lights_off = False
        both_sides_on = False
        
        if any_light:
            if found_light is None:
                found_light = (frame_num, red_light, green_light, left_white_light, right_white_light)
            else:
                time_expired = frame_num > found_light[0] + fps
                both_sides_on = (red_light or left_white_light) and (green_light or right_white_light)
        else:
            if found_light is not None:
                lights_off = True
            
        if time_expired or lights_off or both_sides_on:
            if time_expired or lights_off:
                # Go back to the first frame where the light came on.
                frame_num, red_light, green_light, left_white_light, right_white_light = found_light
    
            refereed = ((red_light and (green_light or right_white_light)) or
                (green_light and left_white_light))
    
            start_frame = max(0, frame_num - int(5 * fps))
            # Don't overlap clips.
            if start_frame > last_found:
                minutes, seconds = get_min_sec(frame_num, fps)
                #print("Found", start_frame, frame_num, f"{minutes}:{seconds}", left_white_light, red_light, right_white_light, green_light, file=found_file)
                df = df._append({'start_frame': start_frame, 
                                 'touch_frame': frame_num, 
                                 'time': f"{minutes}:{seconds:02}",
                                 'left_white': left_white_light, 
                                 'right_white': right_white_light, 
                                 'red': red_light, 
                                 'green': green_light,
                                 'refereed':refereed}, ignore_index=True)
                last_found = frame_num
            found_light = None
    
        # Print status every 10 seconds worth of video
        processed_frames += 5
        if processed_frames % int(60 * fps) == 0:
            print(f"Processed {int(processed_frames // (fps*60))} minutes worth of video")
    
    # Save the results to a CSV file
    df.to_csv(fdir + "/" + prefix + '_frame_data.csv', index=False)
    
    if debug:
        dfile.close()
        
    #found_file.close()
    
    # Release the video capture object
    cap.release()
    
    print(f"Processing complete. Results saved to {prefix}_frame_data.csv")

if __name__ == "__main__":
    prefix = "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red"
    main(prefix)