import cv2
import numpy as np
from scipy.signal import wiener

# Open the input video file
input_video_path = 'touch4475.mp4'
cap = cv2.VideoCapture(input_video_path)

# Check if the video file opened successfully
if not cap.isOpened():
    print("Error opening video file")
    exit()

# Get video properties
fps = int(cap.get(cv2.CAP_PROP_FPS))
width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

# Define the codec and create VideoWriter object to save the new video
output_video_path = 'touch4475_deblur.mp4'
fourcc = cv2.VideoWriter_fourcc(*'mp4v')
out = cv2.VideoWriter(output_video_path, fourcc, fps, (width, height))

# Process each frame
while True:
    ret, frame = cap.read()
    if not ret:
        break

    # Split the frame into its color channels
    b, g, r = cv2.split(frame)
    
    # Apply the Wiener filter to each channel
    deblurred_b = wiener(b, mysize=(5, 5))
    deblurred_g = wiener(g, mysize=(5, 5))
    deblurred_r = wiener(r, mysize=(5, 5))
    
    # Merge the channels back together
    deblurred_frame = cv2.merge((deblurred_b, deblurred_g, deblurred_r))
    
    # Convert to uint8
    deblurred_frame = np.uint8(deblurred_frame)
    
    # Write the deblurred frame to the output video
    out.write(deblurred_frame)

# Release the video capture and writer objects
cap.release()
out.release()

print(f"Deblurred video saved to {output_video_path}")
