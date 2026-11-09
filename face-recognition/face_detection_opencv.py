"""Strict YuNet-based face detection and registration quality checks."""
import os
import shutil
import tempfile
import cv2
import numpy as np


class FaceQualityError(ValueError):
    pass


class FaceDetector:
    def __init__(self, model_path=None, confidence_threshold=0.90):
        base = os.path.dirname(os.path.abspath(__file__))
        self.model_path = model_path or os.path.join(base, "models", "face_detection_yunet_2023mar.onnx")
        if not os.path.exists(self.model_path):
            raise RuntimeError("YuNet model is missing: " + self.model_path)
        try:
            self.model_path.encode("ascii")
        except UnicodeEncodeError:
            safe_dir = os.path.join(tempfile.gettempdir(), "attendance-face-models")
            os.makedirs(safe_dir, exist_ok=True)
            safe_path = os.path.join(safe_dir, os.path.basename(self.model_path))
            if not os.path.exists(safe_path) or os.path.getsize(safe_path) != os.path.getsize(self.model_path):
                shutil.copyfile(self.model_path, safe_path)
            self.model_path = safe_path
        self.detector = cv2.FaceDetectorYN.create(self.model_path, "", (320, 320), confidence_threshold, 0.3, 5000)
        print("[OK] YuNet face detector loaded")

    def detect_faces_detailed(self, image):
        if image is None or image.size == 0:
            return []
        height, width = image.shape[:2]
        self.detector.setInputSize((width, height))
        _, faces = self.detector.detect(image)
        return [] if faces is None else [row.astype(np.float32) for row in faces]

    def detect_faces(self, image):
        return [[int(v) for v in face[:4]] for face in self.detect_faces_detailed(image)]

    def validate_single_front_face(self, image):
        faces = self.detect_faces_detailed(image)
        if not faces:
            raise FaceQualityError("未检测到人脸，请使用清晰的正面照片")
        if len(faces) != 1:
            raise FaceQualityError("画面中检测到多张人脸，请确保只有本人入镜")

        face = faces[0]
        x, y, width, height = face[:4]
        image_height, image_width = image.shape[:2]
        area_ratio = width * height / float(image_width * image_height)
        if min(width, height) < 72 or area_ratio < 0.018:
            raise FaceQualityError("人脸距离镜头太远，请靠近一些并保持脸部完整")
        if area_ratio > 0.70:
            raise FaceQualityError("人脸距离镜头太近，请稍微后退")

        left_eye = face[4:6]
        right_eye = face[6:8]
        nose = face[8:10]
        left_mouth = face[10:12]
        right_mouth = face[12:14]
        eye_distance = float(np.linalg.norm(right_eye - left_eye))
        if eye_distance < 22:
            raise FaceQualityError("眼部特征不够清晰，请正对镜头并靠近一些")

        roll = abs(np.degrees(np.arctan2(right_eye[1] - left_eye[1], right_eye[0] - left_eye[0])))
        eye_mid = (left_eye + right_eye) / 2.0
        yaw_ratio = abs(float(nose[0] - eye_mid[0])) / eye_distance
        mouth_mid = (left_mouth + right_mouth) / 2.0
        vertical_ratio = float((nose[1] - eye_mid[1]) / max(mouth_mid[1] - eye_mid[1], 1.0))
        if roll > 15 or yaw_ratio > 0.34 or not 0.35 <= vertical_ratio <= 0.72:
            raise FaceQualityError("请保持正脸：不要歪头、低头、仰头或明显侧脸")

        x1, y1 = max(0, int(x)), max(0, int(y))
        x2, y2 = min(image_width, int(x + width)), min(image_height, int(y + height))
        gray_face = cv2.cvtColor(image[y1:y2, x1:x2], cv2.COLOR_BGR2GRAY)
        blur_score = float(cv2.Laplacian(gray_face, cv2.CV_64F).var())
        brightness = float(gray_face.mean())
        if blur_score < 55:
            raise FaceQualityError("照片较模糊，请保持稳定并重新拍摄")
        if brightness < 45:
            raise FaceQualityError("画面过暗，请增加正面光线后重新拍摄")
        if brightness > 220:
            raise FaceQualityError("画面过亮，请避免强光直射后重新拍摄")

        return face, {"detectionConfidence": round(float(face[-1]), 4), "blurScore": round(blur_score, 1), "brightness": round(brightness, 1)}
