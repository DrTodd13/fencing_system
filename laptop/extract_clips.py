import pandas as pd
import cv2
import os
import sys
from single_step import single_step

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
    
    # Open the mp4 file with OpenCV
    cap = cv2.VideoCapture(fdir + "/" + prefix + '.mp4')
    #fps = int(cap.get(cv2.CAP_PROP_FPS))
    fps_double = cap.get(cv2.CAP_PROP_FPS)
    # Get the width and height of the video
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    #width_double = cap.get(cv2.CAP_PROP_FRAME_WIDTH)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    #height_double = cap.get(cv2.CAP_PROP_FRAME_HEIGHT)
    
    # Check if the video file opened successfully
    if not cap.isOpened():
        print("Error opening video file")
        sys.exit()
    
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    sscap = single_step(cap)
    
    # Iterate through the dataframe
    for index, row in df.iterrows():
        start_frame = row['start_frame']
        touch_frame = row['touch_frame']
        right_of_way = row['right_of_way']
        if right_of_way == 0:
            continue
    
        print(f"Frame {touch_frame} {index}")
        
        # Set the frame position to start_frame
        sscap.set(start_frame)
    
        output_video_path = f"{fdir}\\{prefix}_touch{touch_frame}.mp4"
        touch_frame_out = cv2.VideoWriter(output_video_path, fourcc, fps_double, (width, height))
    
        # Read frames from start_frame to touch_frame
        print("Capturing frame", end = " ")
        while sscap.get() <= touch_frame:
            ret, frame = sscap.read()
            if not ret:
                print("Error reading frame")
                break
    
            touch_frame_out.write(frame)
        print("")
        touch_frame_out.release()
      
    # Release the video capture object
    cap.release()

if __name__ == "__main__":
    prefix = "C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red"
    main(prefix)