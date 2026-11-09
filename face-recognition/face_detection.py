"""
人脸检测模块
使用dlib检测人脸和68个特征点
"""

import cv2
import dlib
import numpy as np


class FaceDetector:
    """人脸检测器"""

    def __init__(self):
        # 初始化dlib的人脸检测器和关键点检测器
        self.detector = dlib.get_frontal_face_detector()
        self.predictor = None  # 需要下载shape_predictor_68_face_landmarks.dat

        # 预测模型路径（需手动下载）
        self.model_path = 'shape_predictor_68_face_landmarks.dat'

        # 尝试加载预测模型
        try:
            self.predictor = dlib.shape_predictor(self.model_path)
            print(f"人脸预测模型加载成功: {self.model_path}")
        except RuntimeError:
            print(f"警告: 无法加载预测模型 {self.model_path}")
            print("请从以下地址下载模型文件:")
            print("http://dlib.net/files/shape_predictor_68_face_landmarks.dat.bz2")

    def detect_faces(self, image):
        """
        检测图片中的人脸

        Args:
            image: OpenCV图像对象

        Returns:
            list: 人脸矩形列表
        """
        if image is None:
            return []

        # 转换为灰度图
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        else:
            gray = image

        # 检测人脸
        faces = self.detector(gray, 1)

        # 转换为numpy数组
        face_rects = []
        for face in faces:
            face_rects.append(np.array([face.left(), face.top(), face.right(), face.bottom()]))

        return face_rects

    def detect_landmarks(self, image, face_rect):
        """
        检测人脸的68个关键点

        Args:
            image: OpenCV图像对象
            face_rect: 人脸矩形

        Returns:
            numpy.ndarray: 68个关键点的坐标
        """
        if self.predictor is None:
            # 如果预测模型未加载，返回空数组
            return np.zeros((68, 2), dtype=np.int32)

        # 转换为dlib rectangle
        dlib_rect = dlib.rectangle(
            face_rect[0], face_rect[1], face_rect[2], face_rect[3]
        )

        # 检测关键点
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        landmarks = self.predictor(gray, dlib_rect)

        # 转换为numpy数组
        points = np.zeros((68, 2), dtype=np.int32)
        for i in range(68):
            points[i] = [landmarks.part(i).x, landmarks.part(i).y]

        return points

    def get_eye_landmarks(self, landmarks):
        """
        获取眼部关键点

        Args:
            landmarks: 68个关键点

        Returns:
            tuple: (左眼关键点, 右眼关键点)
        """
        # 左眼关键点索引: 36-41
        left_eye = landmarks[36:42]

        # 右眼关键点索引: 42-47
        right_eye = landmarks[42:48]

        return left_eye, right_eye

    def get_nose_tip(self, landmarks):
        """
        获取鼻尖位置（第30个点）

        Args:
            landmarks: 68个关键点

        Returns:
            tuple: (x, y) 鼻尖坐标
        """
        return tuple(landmarks[30])

    def draw_landmarks(self, image, landmarks, color=(0, 255, 0), thickness=1):
        """
        在图像上绘制关键点

        Args:
            image: OpenCV图像对象
            landmarks: 68个关键点
            color: 颜色
            thickness: 线条粗细

        Returns:
            image: 绘制了关键点的图像
        """
        for i, (x, y) in enumerate(landmarks):
            cv2.circle(image, (x, y), 2, color, thickness)

        # 绘制眼睛轮廓
        left_eye = landmarks[36:42]
        right_eye = landmarks[42:48]
        cv2.polylines(image, [left_eye], True, (255, 0, 0), 2)
        cv2.polylines(image, [right_eye], True, (255, 0, 0), 2)

        # 绘制鼻尖
        nose_tip = landmarks[30]
        cv2.circle(image, tuple(nose_tip), 5, (0, 0, 255), -1)

        return image