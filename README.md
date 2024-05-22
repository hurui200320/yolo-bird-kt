# yolo-bird-kt
Use yolo to detect birds, on kotlin jvm.

```bash
apt update && apt install openjdk-21-jdk python3 python3-pip -y
pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip3 install ultralytics

wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt

yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

```

## AWS
g4dn.xlarge 4vCPU, 16GB RAM, 16 GB VRAM
Deep Learning Base OSS Nvidia Driver GPU AMI (Ubuntu 22.04) 20240513 (user: ubuntu)
自动分配公网IP
75GB
IAM S3权限

```bash
# by default nvme will mount to here
cd /opt/dlami/nvme
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y openjdk-21-jdk htop python3-pip git glances
sudo update-java-alternatives --set /usr/lib/jvm/java-1.21.0-openjdk-amd64
git clone https://github.com/hurui200320/yolo-bird-kt.git
# use mv to download and delete data
aws s3 cp --recursive s3://skyblond-yolo-bird/chunk /opt/dlami/nvme/chunk/

pip3 install torch torchvision torchaudio ultralytics
wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt
export PATH=$PATH:/home/ubuntu/.local/bin
yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

cd yolo-bird-kt
chmod +x ./gradlew
export AWS_EC2=true
./gradlew runDetect

aws s3 cp --recursive /opt/dlami/nvme/bird/ s3://skyblond-yolo-bird/bird/
```

TODO: FFMPEG?
TODO: End to end: fetch video from s3, chunk, detect, discard boring, concat bird, post to YouTube (both raw and clip concat)
TODO: Boot up at 3AM every day? Check s3, do the thing, shutdown?


