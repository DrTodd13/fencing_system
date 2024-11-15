"""
Capture frames from the webcam.  Run yolo11 pose estimation.
"""
import cv2
import torch
import numpy as np
from PIL import Image
from torchvision import transforms
from ultralytics import YOLO
import time
import os

yolo_pose = True

dir_path = os.path.dirname(os.path.realpath(__file__))
if yolo_pose:
    yolo_file = dir_path + '\\yolo11n-pose.pt'
else:
    yolo_file = dir_path + '\\yolo11n.pt'

# Initialize YOLO model
yolo_model = YOLO(yolo_file)

# Initialize OpenPose model
#openpose_model = BodyPoseModel('path_to_openpose_weights')
#openpose_model.load_state_dict(torch.load('path_to_openpose_weights'))

# Initialize the webcam
cap = cv2.VideoCapture(0)

# Capture variable seconds of video
frames = []
start_time = time.time()
capture_length = 3
while time.time() - start_time < capture_length:
    ret, frame = cap.read()
    if not ret:
        break
    frames.append(frame)

frames_per_second = len(frames) / capture_length

cap.release()
blue = (255, 0, 0)
red = (0, 255, 0)
colors = [blue, red]

def connect(frame, p1, p2, color):
    thickness = 2  # Thickness of 2 px
    p1x, p1y = p1
    p2x, p2y = p2
    if p1x == 0 and p1y == 0:
        return
    if p2x == 0 and p2y == 0:
        return
    cv2.line(frame, tuple(p1.astype(int)), tuple(p2.astype(int)), color, thickness)
    
def drawLines(frame, kps, color):
    connect(frame, kps[5], kps[7], color)
    connect(frame, kps[6], kps[8], color)
    connect(frame, kps[7], kps[9], color)
    connect(frame, kps[8], kps[10], color)
    connect(frame, kps[5], kps[6], color)
    connect(frame, kps[5], kps[11], color)
    connect(frame, kps[6], kps[12], color)
    connect(frame, kps[11], kps[13], color)
    connect(frame, kps[12], kps[14], color)
    connect(frame, kps[13], kps[15], color)
    connect(frame, kps[14], kps[16], color)
    connect(frame, kps[0], kps[5], color)
    connect(frame, kps[0], kps[6], color)
    connect(frame, kps[11], kps[12], color)

draw_lines = True

def calculate_angle(pointA, pointB, pointC):
    # Convert the points to numpy arrays
    a = np.array(pointA)
    b = np.array(pointB)
    c = np.array(pointC)
    
    # Calculate the vectors AB and BC
    AB = b - a
    BC = b - c
    
    # Calculate the dot product and magnitudes of AB and BC
    dot_product = np.dot(AB, BC)
    magnitude_AB = np.linalg.norm(AB)
    magnitude_BC = np.linalg.norm(BC)
    
    # Calculate the cosine of the angle
    cos_angle = dot_product / (magnitude_AB * magnitude_BC)
    
    # Calculate the angle in radians and convert to degrees
    angle = np.arccos(cos_angle)
    angle_degrees = np.degrees(angle)
    #print(AB, BC, dot_product, magnitude_AB, magnitude_BC, cos_angle, angle)

    return angle_degrees

def leg_angle(player, knee, hip, ankle):
    if player[knee] == 0 or player[hip] == 0 or player[ankle] == 0:
        return 0
    return 1 if (player0x[14] > (player0x[12] + player0x[16])/2) else -1

#cv2.putText(img_cv, f'{label} {conf:.2f}', (int(x1), int(y1) - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 0), 2)

# Apply skeletal tracking to the captured frames
processed_frames = []
for frame in frames:
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    pil_image = Image.fromarray(rgb_frame)
    results = yolo_model(pil_image, verbose=False)
    for result in results:
        if yolo_pose:
            # 0: Nose 1: Left Eye 2: Right Eye 3: Left Ear 4: Right Ear 
            # 5: Left Shoulder 6: Right Shoulder 
            # 7: Left Elbow 8: Right Elbow 9: Left Wrist 10: Right Wrist 
            # 11: Left Hip 12: Right Hip 13: Left Knee 14: Right Knee 
            # 15: Left Ankle 16: Right Ankle
            if len(results.keypoints) == 2:
                torso = [5, 6, 11, 12]
                player0x = result.keypoints.xy[0].numpy()[:,0]
                player1x = result.keypoints.xy[1].numpy()[:,0]
                player0average = np.mean(player0x[player0x != 0])
                player1average = np.mean(player1x[player1x != 0])
                torso0 = player0x[torso]
                torso1 = player1x[torso]
                player0minimum = np.min(torso0[torso0 != 0])
                player1minimum = np.min(torso1[torso1 != 0])
                player0left = leg_angle(player0x, 13, 11, 15)
                player1left = leg_angle(player1x, 13, 11, 15)
                player0right = leg_angle(player0x, 14, 12, 16)
                player1right = leg_angle(player1x, 14, 12, 16)
                
                leftright = player0average < player1average
                if player0minimum < player1minimum != leftright:
                    print("torso minimum contradicts overall average")
                if player0left != 0 and ((player0left != 1) != leftright):
                    print("torso minimum contradicts player0left")
                if player0right != 0 and ((player0right != 1) != leftright):
                    print("torso minimum contradicts player0right")
                if player1left != 0 and ((player1left != -1) != leftright):
                    print("torso minimum contradicts player1left")
                if player1right != 0 and ((player1right != -1) != leftright):
                    print("torso minimum contradicts player1right")
                    
                drawLines(frame, result.keypoints.xy[0] if leftright else result.keypoints.xy[1], colors[0 if leftright else 1])
                drawLines(frame, result.keypoints.xy[1] if leftright else result.keypoints.xy[0], colors[1 if leftright else 0])
            else:
                
                for objnum in range(len(result.keypoints)):
                    kps = result.keypoints.xy[objnum].numpy()
                    if draw_lines:
                        drawLines(frame, kps, colors[objnum%len(colors)])
                    else:
                        for kp in kps:
                            x, y = kp
                            cv2.circle(frame, (int(x), int(y)), 5, (0, 255, 0), -1)
                
        else:
            for objnum in range(len(result.boxes)):
                if result.names[result.boxes.cls.numpy()[objnum]] == 'person':
                    person = result.boxes.xyxy[objnum]
                    x1, y1, x2, y2 = person.numpy()[:4].astype(int)
                    cropped_person = pil_image.crop((x1, y1, x2, y2))
                    cropped_person = transforms.ToTensor()(cropped_person)
                    cropped_person = cropped_person.unsqueeze(0)
                    #pose_results = openpose_model(cropped_person)
                    #for landmark in pose_results:
                    #    for (x, y) in landmark:
                    #        cv2.circle(frame, (int(x + x1), int(y + y1)), 5, (0, 255, 0), -1)
                    cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
    processed_frames.append(frame)

# Repeatedly show the 5 seconds of video
keep_going = True
while keep_going:
    for frame in processed_frames:
        cv2.imshow('Skeletal Tracking', frame)
        if cv2.waitKey(int(1000/frames_per_second)) & 0xFF == ord('q'):
            #cap.release()
            cv2.destroyAllWindows()
            keep_going = False
            break

