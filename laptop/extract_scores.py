"""
Go through the Shanghai video and collect images for the left
and right scores (0-15) from the overlay.
"""
import cv2
import numpy as np
import os
import time

use_easyocr = False
if use_easyocr:
    import easyocr
    reader = easyocr.Reader(['en'])

    def ocrnum(img):
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        result = reader.readtext(img, allowlist='0123456789')
        if result:
            score_text = result[0][-2]
            try:
                score = int(score_text)
                return score
            except ValueError:
                return -2
        else:
            return -1
    
# If duplicates then decrease value.
# If missing values then increase value.
def images_are_similar(img1, img2, threshold=-3.0):
    if img1.shape != img2.shape:
        return False
    
    difference = cv2.absdiff(img1, img2)
    difference[difference <= 40] = 0
    difference[difference >  40] = 255
    similarity = 1 - np.sum(difference) / np.prod(img1.shape)
    return similarity >= threshold


"""
image_path1 = 'left/left_15840.jpg'  # Replace with the path to your first image
image_path2 = 'left/left_18320.jpg'  # Replace with the path to your second image
img1 = cv2.imread(image_path1)
img2 = cv2.imread(image_path2)
images_are_similar(img1, img2)
sys.exit(0)
"""

def main(prefix):
    """
    Finds and saves left and right side score numbers.
    """
    # Load the video file
    video_path = prefix + '.mp4'
    cap = cv2.VideoCapture(video_path)
    
    # Check if the video opened successfully
    if not cap.isOpened():
        print("Error: Could not open video.")
        exit()
    
    # Get the frame rate of the video
    fps = cap.get(cv2.CAP_PROP_FPS)
    print("fps:", fps)
        
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    # Create directories for storing images if they don't exist
    if not os.path.exists('left'):
        os.makedirs('left')
    if not os.path.exists('right'):
        os.makedirs('right')
    
    # Define coordinates for the rectangles
    left_coords = ((260, 306), (280, 321))
    right_coords = ((357, 306), (377, 321))
    
    # Store previous rectangles
    left_rectangles = []
    right_rectangles = []
    processed_frames = 0
    
    last_left = None
    last_right = None
    
    start = time.time()
    # Process every fifth frame
    for frame_num in range(0, frame_count, 5):
        # Set the video to the specific frame
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
        
        # Read the frame
        ret, frame = cap.read()
        if not ret:
            break
        
        left_rect = frame[left_coords[0][1]:left_coords[1][1], left_coords[0][0]:left_coords[1][0]]
        right_rect = frame[right_coords[0][1]:right_coords[1][1], right_coords[0][0]:right_coords[1][0]]
    
        if use_easyocr:
            left_score = ocrnum(left_rect)
            right_score = ocrnum(right_rect)
            
            if left_score != last_left or right_score != last_right:
                print(left_score, right_score)
                
            if last_left is None:
                last_left = left_score
            if last_right is None:
                last_right = right_score
                
            if left_score != 0:
                if abs(left_score - last_left) > 1:
                    print("JUMP IN LEFT SCORE")
            if right_score != 0:
                if abs(right_score - last_right) > 1:
                    print("JUMP IN RIGHT SCORE")    
        else:
            # Handle left rectangle
            if not any(images_are_similar(left_rect, prev_rect) for prev_rect in left_rectangles):
                print("Found unique left at frame", frame_num)
                left_rectangles.append(left_rect)
                left_img_path = os.path.join('left', f'left_{frame_num}.jpg')
                cv2.imwrite(left_img_path, left_rect)
        
            # Handle right rectangle
            if not any(images_are_similar(right_rect, prev_rect) for prev_rect in right_rectangles):
                print("Found unique right at frame", frame_num)
                right_rectangles.append(right_rect)
                right_img_path = os.path.join('right', f'right_{frame_num}.jpg')
                cv2.imwrite(right_img_path, right_rect)
                    
            if len(left_rectangles) >= 16 and len(right_rectangles) >= 16:
                break
        
        # Print status every 10 seconds worth of video
        processed_frames += 5
        if processed_frames % (60 * fps) == 0:
            cur_time = time.time()
            print(f"Processed {processed_frames // (fps*60)} minutes worth of video {processed_frames // (cur_time-start)}fps")
    
    
    # Release the video capture object
    cap.release()
    
    print("Processing complete. Unique rectangles saved.")

if __name__ == "__main__":
    prefix = "Shanghai_Yellow"
    main(prefix)