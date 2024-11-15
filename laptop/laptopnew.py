"""
Use mediapipe to do skeletal tracking.  This is insufficient
because mediapipe only tracks one person.
"""
import cv2
import mediapipe as mp

# Initialize MediaPipe Pose solution
mp_pose = mp.solutions.pose
pose = mp_pose.Pose()

# Initialize MediaPipe Drawing solution
mp_drawing = mp.solutions.drawing_utils

# Initialize the webcam
cap = cv2.VideoCapture(0)

first = True

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    # Convert the BGR image to RGB
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    # Process the image and find the poses
    results = pose.process(rgb_frame)

    if results.pose_landmarks:
        if first:
            print("1", type(results.pose_landmarks), hasattr(results.pose_landmarks, "landmark"))
            print(dir(results.pose_landmarks))
            print(results.pose_landmarks.landmark)
            print(type(results.pose_landmarks.landmark))
            print(dir(results.pose_landmarks.landmark))
            first = False
            
        landmarks = list(results.pose_landmarks.landmark)
        
        # Calculate distances of each pose to the camera
        pose_dists = [(i, lmk.z) for i, lmk in enumerate(landmarks)]
        pose_dists.sort(key=lambda x: x[1])  # Sort by distance (z value)

        # Keep only the two closest poses
        closest_poses = pose_dists[:2]

        # Draw landmarks for the two closest poses
        for i, _ in closest_poses:
            mp_drawing.draw_landmarks(frame, landmarks[i], mp_pose.POSE_CONNECTIONS)

    # Display the output frame
    cv2.imshow('Skeletal Tracking', frame)

    # Break the loop if the user presses the 'q' key
    if cv2.waitKey(10) & 0xFF == ord('q'):
        break

# Release the webcam and close the window
cap.release()
cv2.destroyAllWindows()
