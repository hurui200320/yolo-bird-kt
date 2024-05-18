# yolo-bird-kt
Use yolo to detect birds, on kotlin jvm.

```bash
apt update && apt install openjdk-21-jdk python3 python3-pip -y
pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip3 install ultralytics

wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt

yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

```
