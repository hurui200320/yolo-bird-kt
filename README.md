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
RTX 4090 (24GB), pytorch 2.2.0 cuda 12.1, 100GB, backblaze cloud

```bash
# by default nvme will mount to here
cd /workspace
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y openjdk-21-jdk htop git glances
git clone https://github.com/hurui200320/yolo-bird-kt.git

pip3 install ultralytics
wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt
yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

cd yolo-bird-kt
chmod +x ./gradlew
./gradlew installDist && cp coco.names build/install/yolo-bird-kt/
./build/install/yolo-bird-kt/bin/yolo-bird-kt

aws s3 cp --recursive /opt/dlami/nvme/bird/ s3://skyblond-yolo-bird/bird/
```

TODO: FFMPEG?
TODO: End to end: fetch video from s3, chunk, detect, discard boring, concat bird, post to YouTube (both raw and clip concat)
TODO: Boot up at 3AM every day? Check s3, do the thing, shutdown?


