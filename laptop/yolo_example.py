import cv2
import torch
from PIL import Image
from torchvision import transforms
import matplotlib.pyplot as plt

# Load YOLO model (using a pre-trained YOLOv5 model here)
model = torch.hub.load('ultralytics/yolov5', 'yolov5s')

# Load an image
img_path = 'path_to_your_image.jpg'
img = Image.open(img_path)

# Preprocess the image
transform = transforms.Compose([transforms.ToTensor()])
img_tensor = transform(img).unsqueeze(0)

# Perform inference
results = model(img_tensor)

# Extract bounding boxes and labels
boxes = results.xyxy[0].cpu().numpy()
labels = results.names

# Read image with OpenCV for drawing
img_cv = cv2.imread(img_path)

# Draw bounding boxes
for box in boxes:
    x1, y1, x2, y2, conf, cls = box
    label = labels[int(cls)]
    if label == 'person':
        cv2.rectangle(img_cv, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)
        cv2.putText(img_cv, f'{label} {conf:.2f}', (int(x1), int(y1) - 10), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 0), 2)

# Display the image
plt.imshow(cv2.cvtColor(img_cv, cv2.COLOR_BGR2RGB))
plt.axis('off')
plt.show()
