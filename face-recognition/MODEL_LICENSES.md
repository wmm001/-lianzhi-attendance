# Face model sources

This service uses official models from the OpenCV Model Zoo:

- YuNet face detector: `models/face_detection_yunet_2023mar.onnx`
  - Source: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
  - Model license: MIT
- SFace face recognizer: `models/face_recognition_sface_2021dec.onnx`
  - Source: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
  - Repository license: Apache-2.0; a copy of the repository license is included at `models/LICENSE-opencv-zoo.txt`.

OpenCV Model Zoo: https://github.com/opencv/opencv_zoo

- MediaPipe Face Landmarker (for blink and head-turn checks): `models/face_landmarker.task`
  - Official model bundle: https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
  - Documentation and model information: https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker
