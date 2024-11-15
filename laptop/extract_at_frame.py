"""
Extract a frame at a specific time from the video and save it
to an image file.
"""
import cv2

# Load the video file
video_path = 'C:\\Users\\drtod\\Documents\\fencing\\Shanghai_Red\\Shanghai_Red.mp4'
cap = cv2.VideoCapture(video_path)

# Check if the video opened successfully
if not cap.isOpened():
    print("Error: Could not open video.")
    exit()

# Convert time to seconds (1 minute 59 seconds)
time_in_seconds = 3 * 60 + 23

# Get the frame rate of the video
fps = cap.get(cv2.CAP_PROP_FPS)
#total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
#total_time = 5 * 3600 + 30 * 60 + 26
#calculated_fps = total_frames / total_time
#print(calculated_fps)
# Calculate the frame number to capture
frame_number = int(fps * time_in_seconds)

# Set the video to the specific frame
cap.set(cv2.CAP_PROP_POS_FRAMES, frame_number)

# Read the frame
ret, frame = cap.read()
if not ret:
    print("Error: Could not read frame.")
    exit()

# Save the frame as a JPG file
output_path = 'red_frame_at_3min23sec.jpg'
cv2.imwrite(output_path, frame)

# Release the video capture object
cap.release()