# yolo-bird-kt

Originally uses yolo to detect birds, on kotlin jvm.
You can use it to detect anything.

## Requirement

+ Java 11 or higher
+ ONNX Runtime 1.18.0
  + Automatically download as a dependency
  + Uses `com.microsoft.onnxruntime:onnxruntime_gpu`
+ CUDA 12 and cuDNN 8
  + Automatically download as dependency if not omitted by env `GRADLE_BUILD_OMIT_CUDA=1`
  + ONNX runtime offers other backends, but this code is hard coded to use CUDA
  + Check https://onnxruntime.ai/docs/get-started/with-java.html and 
    create an issue if you need other cards supported
  + Use `org.bytedeco:cuda-platform` and `org.bytedeco:cuda-platform-redist`
+ YOLOv8 model
  + The input shape is `[batch, channel, height, width]`
  + The output shape is `[batch, 4 + labels, anchors]`,
    the first 4 elements are for box's `x,y,w,h`.
    The x and y means the center point of the bounding box,
    and w and h means the width and height of the bounding box.
  + The tool can automatically read batch size, height and width from model.
    But it won't apply scale to the input, only padding black pixels.
    Ideally, you can have a 1920x1080 input with a 1920x1088 model.
  + The channel must be 3, and 0 is for Red, 1 is for Green, 2 is for Blue.
+ FFmpeg available in the PATH, or specified by cli option.
  + The minimal requirement for FFmpeg is to be able to segment and concat videos
    using the `segment` muxer and `concat` demuxer.
  + The ffmpeg for video decoding will be downloaded as a dependency.
+ A lot of RAM and CPU.
  + When inferencing, this tool will at least load one video fully in RAM.
  + This tool also uses kotlin coroutine to speed up things by doing them at the same time.

## Build

If you don't want to install CUDA and cuDNN, just build with the following command:

```shell
./gradlew installDist
```

If you have CUDA 12 and cuDNN 8 installed, you can skip the dependency for them:

```shell
export GRADLE_BUILD_OMIT_CUDA=1
./gradlew installDist
```

## My use case

I wrote this tool simply because I feed birds at my backyard and set up a
monitoring camera using my old phone.
I record right after I wake up and until the sun sets, producing more than
10 hours of recordings every day.
I have no idea of what to do with them, but also don't want to delete them,
or spending hours to find which birds come.
Thus I wrote this tool to automatically chunk the recording into small pieces,
apply yolo on them, deleting the boring clips (no birds shows up), then
concat the rest back to one piece.
So I can keep the raw footage along with the processed one.
That allows me to spend less than 2 hours to watch the highlights generated by yolo:
only watching the birds and waste almost no time on boring clips.

However, the yolo model is not 100% accurate.
Sometimes it gives false positive, which will be included in the final video.
Sometimes it gives false negative, which will be missing in the final video.
During the early debug stage, I found out that false positive is much commoner than false negative,
based on my set-up.
That's why I will keep the raw footage in case the model deleted the clip containing undetected birds.

## My workflow

I'm recording footage using an app called "[IP webcam Pro](https://play.google.com/store/apps/details?id=com.pas.webcam.pro&hl=en_US)",
it will record mp4 files and name them like `rec_2024-05-16_13-54.mp4`.
At the end of the day, I'll copy the footage into the `raw` folder under the working dir,
and run this command to get 1 minute long clips:

```shell
yolo-bird-kt -w /workspace -f mp4 -s PT1M 
```

Then upload the `clip` folder to a backblaze bucket.
I have a RTX 3070 Mobile for my laptop, but it's too slow and constantly over heating,
so I decide to rent a GPU on vast.ai.
I tried AWS, it's expensive and performs bad.
Vast.ai didn't pay me to say this, but it is the platform I'm confortable with.
If you like to support me, you can use this referral link:
https://cloud.vast.ai/?ref_id=61326

After setting up the cloud sync feature on vast.ai, I normally rent a RTX 4090
from a data center located in Norway. Usually less than 0.5 USD/Hour.

### Prepare model

I'm using yolov8x with coco dataset:

```shell
sudo apt update && sudo apt install -y python3-pip
pip3 install torch torchvision torchaudio ultralytics
wget https://github.com/ultralytics/assets/releases/download/v8.2.0/yolov8x.pt
yolo export model=yolov8x.pt format=onnx imgsz=1080,1920 batch=10
```

Then upload the onnx model to the backblaze so you can reuse the model.

### vast.ai

Here is my full set up:
+ RTX 4090 (24G VRAM)
+ 31 CPU cores
+ 41GB RAM
+ base image: nvidia/cuda:12.2.2-cudnn8-runtime-ubuntu22.04
+ Disk size 100GB
+ backblaze cloud sync to `/workspace`

It's important to have `cudnn8` in the docker image, otherwise ONNX runtime will
throw errors on the wrong cuda version.

The setup command is:
```bash
touch ~/.no_auto_tmux
cd /workspace
DEBIAN_FRONTEND=noninteractive
sudo apt update && sudo apt full-upgrade -y
sudo apt install -y openjdk-21-jdk htop git glances ffmpeg
git clone https://github.com/hurui200320/yolo-bird-kt.git

cd /workspace/yolo-bird-kt
git pull && chmod +x ./gradlew
export GRADLE_BUILD_OMIT_CUDA=1
./gradlew installDist

./build/install/yolo-bird-kt/bin/yolo-bird-kt \
    -w /workspace \
    -f mp4 \
    -i bird:0.65 \
    -n 0.7 \
    --skip-frame 6 \
    --buffered-video 1 \
    -p /workspace/yolov8x.onnx \
    -c 
    
rm -rf yolo* raw clip
# then upload the evidence and interest folder to b2 bucket
```

Or locally on Windows:

```powershell
./build/install/yolo-bird-kt/bin/yolo-bird-kt -w D:\modet -f mp4 -i bird:0.65 -n 0.7 --skip-frame 12 --buffered-video 2 -p D:\code\PycharmProjects\yolov8\yolov8x.onnx -c
```

The last command will first process (`-p`) all clips, delete the boring one
and only leaves the clips which has brids showing up.
Then concat (`-c`) the clips back to one giant video file.

Finally, sync the `/workspace` folder back to backblaze bucket,
download the result to my laptop, and call it a day.
