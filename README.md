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
g4dn.xlarge 4vCPU 16GB RAM, 100GB GP3 (安装pytorch之后大约会占用到50GB左右)
Deep Learning OSS Nvidia Driver AMI GPU PyTorch 2.2.0 (Amazon Linux2) 20240517
自动分配公网IP

2023 AMI 50 GB


```bash
sudo mkfs -t xfs /dev/nvme1n1
sudo mkdir /data
sudo mount /dev/nvme1n1 /data
sudo chmod -R 777 /data

cd /data
sudo yum upgrade -y
sudo yum install -y htop python3-pip mesa-libGL git
wget https://corretto.aws/downloads/latest/amazon-corretto-21-x64-linux-jdk.rpm
sudo yum localinstall -y amazon-corretto-21-x64-linux-jdk.rpm

git clone https://github.com/hurui200320/yolo-bird-kt.git
# use mv to download and delete data
aws s3 cp --recursive s3://skyblond-yolo-bird/chunk /data/chunk/

pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip3 install ultralytics glances
wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt
yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10

cd yolo-bird-kt
chmod +x ./gradlew
./gradlew runDetect

aws s3 cp --recursive /data/bird/ s3://skyblond-yolo-bird/bird/
```

TODO: FFMPEG?
TODO: End to end: fetch video from s3, chunk, detect, discard boring, concat bird, post to YouTube (both raw and clip concat)
TODO: Boot up at 3AM every day? Check s3, do the thing, shutdown?


