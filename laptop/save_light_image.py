"""
Extract an image from a frame of the video capturing one of the
white, green, or red lights.
"""
import cv2

# Load the video file
video_path = 'Shanghai_Yellow.mp4'
cap = cv2.VideoCapture(video_path)

# Check if the video opened successfully
if not cap.isOpened():
    print("Error: Could not open video.")
    exit()

# Convert time to seconds (1 minute 59 seconds)
time_in_seconds = 3 * 60 + 17

# Get the frame rate of the video
fps = cap.get(cv2.CAP_PROP_FPS)

# Calculate the frame number to capture
frame_number = int(fps * time_in_seconds)

# Set the video to the specific frame
cap.set(cv2.CAP_PROP_POS_FRAMES, frame_number)

# Read the frame
ret, frame = cap.read()
if not ret:
    print("Error: Could not read frame.")
    exit()

# Define the coordinates for the rectangle
start_point = (200, 328)
end_point = (250, 330)

# Extract the rectangle of interest from the frame
left_red = frame[start_point[1]:end_point[1], start_point[0]:end_point[0]]

# Save the extracted rectangle as an image
cv2.imwrite('left_red.jpg', left_red)

# Release the video capture object
cap.release()

print("Frame at 1:59 extracted and saved as left_white.jpg")
