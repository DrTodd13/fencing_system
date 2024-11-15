"""
Go through every fifth frame of video and find points at which the
score changes and note the new score in a dataframe and the frame
number where the score changed.
"""

import cv2
import numpy as np
import os
#import sys
import time
import pandas as pd
import sys

def img_similarity(img1, img2):
    if img1.shape != img2.shape:
        return -1000000
    
    difference = cv2.absdiff(img1, img2)
    difference[difference <= 40] = 0
    difference[difference >  40] = 255
    similarity = 1 - np.sum(difference) / np.prod(img1.shape)
    return similarity

# If duplicates then decrease value.
# If missing values then increase value.
def images_are_similar(img1, img2, threshold=-3.0):
    return img_similarity(img1, img2) >= threshold
    
def closest_match(img_dict, img):
    best = -99999999999
    best_index = -1
    for k,v in img_dict.items():
        similarity = img_similarity(v, img)
        if similarity > -3.0:
            return k
        if similarity > best:
            best = similarity
            best_index = k
    return best_index

def check_increment(img_dict, img, last_seen):
    if images_are_similar(img_dict[last_seen], img):
        return last_seen
    elif last_seen < 15 and images_are_similar(img_dict[last_seen + 1], img):
        return last_seen + 1 
    elif last_seen > 0 and images_are_similar(img_dict[last_seen - 1], img) :
        return last_seen - 1 
    elif images_are_similar(img_dict[0], img):
        return 0
    else:
        return closest_match(img_dict, img)

class single_step:
    def __init__(self, cap):
        self.cap = cap
        self.next_frame = 0
        
    def set(self, frame_number):
        while self.next_frame < frame_number:
            self.cap.read()
            self.next_frame += 1
            if self.next_frame % 10000 == 0:
                print("single_step", self.next_frame)

    def get(self):
        return self.next_frame
    
    def read(self):
        self.next_frame += 1
        return self.cap.read()
            
def main(prefix):
    fdir = prefix
    prefix = os.path.basename(fdir)
    # Load the video file
    video_path = fdir + "/" + prefix + '.mp4'
    cap = cv2.VideoCapture(video_path)
    sscap = single_step(cap)
    
    # Check if the video opened successfully
    if not cap.isOpened():
        print("Error: Could not open video.")
        sys.exit()
    
    # Get the frame rate of the video
    fps = cap.get(cv2.CAP_PROP_FPS)
    print("fps:", fps)
        
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    print("frame_count:", frame_count)
    
    # Load images from files to dictionaries.
    left_dict = {}
    right_dict = {}
    for i in range(16):
        left_image_path = os.path.join('left', f"left_{i}.jpg")
        right_image_path = os.path.join('right', f"right_{i}.jpg")
        left_dict[i] = cv2.imread(left_image_path)
        right_dict[i] = cv2.imread(right_image_path)
        
    # Create a DataFrame to store the results
    df = pd.DataFrame(columns=['frame_number', 'left_score', 'right_score'])
        
    # Define coordinates for the rectangles
    left_coords = ((260, 306), (280, 321))
    right_coords = ((357, 306), (377, 321))
    
    # Store previous rectangles
    processed_frames = 0
    
    last_left = None
    last_right = None
    
    start = time.time()
    # Process every fifth frame
    for frame_num in range(0, frame_count, 5):
        # Set the video to the specific frame
        #cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
        sscap.set(frame_num)
        
        # Read the frame
        ret, frame = sscap.read()
        if not ret:
            break
        
        left_rect = frame[left_coords[0][1]:left_coords[1][1], left_coords[0][0]:left_coords[1][0]]
        right_rect = frame[right_coords[0][1]:right_coords[1][1], right_coords[0][0]:right_coords[1][0]]
        
        new_left = check_increment(left_dict, left_rect, last_left if last_left is not None else 0)
        new_right = check_increment(right_dict, right_rect, last_right if last_right is not None else 0)
        
        if last_left is None or (last_left != new_left or last_right != new_right):
            if last_left is not None and (not (new_left == 0 and new_right == 0)) and (abs(last_left-new_left) > 1 or abs(last_right-new_right) > 1):
                print(f"Unexpected score change at frame {frame_num}")
            df = df._append({'frame_number': frame_num, 'left_score': new_left, 'right_score': new_right}, ignore_index=True)
            last_left = new_left
            last_right = new_right
            print(f"Left Score: {last_left} Right Score: {last_right} Frame: {frame_num}")
        
        # Print status every 10 seconds worth of video
        processed_frames += 5
        if processed_frames % (60 * fps) == 0:
            cur_time = time.time()
            print(f"Processed {processed_frames // (fps*60)} minutes worth of video {processed_frames // (cur_time-start)}fps")
    
    # Release the video capture object
    cap.release()
    df.to_csv(fdir + "/" + prefix + '_score_data.csv', index=False)
    
    print("Processing complete.")

if __name__ == "__main__":
    prefix = "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red"
    main(prefix)