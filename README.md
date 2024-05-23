# yolo-bird-kt
Use yolo to detect birds, on kotlin jvm.

```bash
apt update && apt install openjdk-21-jdk python3 python3-pip -y
pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip3 install ultralytics

wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt

yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

```

## vast.ai
RTX 4090 (24GB), nvidia/cuda:12.2.2-cudnn8-runtime-ubuntu22.04, 100GB, backblaze cloud
一定要有cudnn
```bash
cd /workspace
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y openjdk-21-jdk htop git python3-pip glances
git clone https://github.com/hurui200320/yolo-bird-kt.git

pip3 install torch torchvision torchaudio ultralytics
wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt
yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

cd yolo-bird-kt
git pull && chmod +x ./gradlew
./gradlew installDist && cp coco.names build/install/yolo-bird-kt/
./build/install/yolo-bird-kt/bin/yolo-bird-kt
```

TODO: FFMPEG?
TODO: End to end: fetch video from s3, chunk, detect, discard boring, concat bird, post to YouTube (both raw and clip concat)
TODO: Boot up at 3AM every day? Check s3, do the thing, shutdown?


