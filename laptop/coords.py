"""
Put a frame on the screen and hover the mouse to identify coords
on the frame.
"""
import cv2

# Callback function to display coordinates
def show_coordinates(event, x, y, flags, param):
    if event == cv2.EVENT_MOUSEMOVE:
        print(f"Mouse coordinates: X: {x}, Y: {y}")

# Load the image
image_path = 'red_frame_at_3min23sec.jpg'
img = cv2.imread(image_path)

# Create a window and set the mouse callback function
cv2.namedWindow('Image')
cv2.setMouseCallback('Image', show_coordinates)

while True:
    cv2.imshow('Image', img)
    # Break the loop if the user presses the 'q' key
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# Destroy the window
cv2.destroyAllWindows()
