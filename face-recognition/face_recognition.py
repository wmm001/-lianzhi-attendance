"""
人脸识别模块
提取人脸特征向量并进行比对
"""

import cv2
import dlib
import numpy as np


class FaceRecognizer:
    """人脸识别器"""

    def __init__(self, features_file=None):
        self.face_encoder = None  # dlib的人脸编码器
        self.features_file = features_file

        # 尝试初始化人脸编码器（需要dlib的人脸识别模型）
        self.model_path = 'dlib_face_recognition_resnet_model_v1.dat'

        try:
            self.face_encoder = dlib.face_recognition_model_v1(self.model_path)
            print(f"人脸识别模型加载成功: {self.model_path}")
        except RuntimeError:
            print(f"警告: 无法加载识别模型 {self.model_path}")
            print("请从以下地址下载模型文件:")
            print("http://dlib.net/files/dlib_face_recognition_resnet_model_v1.dat.bz2")

    def extract_features(self, image, landmarks):
        """
        提取人脸特征向量

        Args:
            image: OpenCV图像对象
            landmarks: 68个关键点

        Returns:
            numpy.ndarray: 128维特征向量
        """
        if self.face_encoder is None:
            # 如果编码器未加载，生成模拟特征向量
            print("警告: 使用模拟特征向量")
            return np.random.rand(128)

        # 转换关键点为dlib格式
        dlib_points = dlib.full_object_detection()
        dlib_points.num_parts = 68
        dlib_points.rect = dlib.rectangle(0, 0, 100, 100)

        points = []
        for i in range(68):
            points.append(dlib.point(int(landmarks[i][0]), int(landmarks[i][1])))

        # 手动设置关键点
        for i in range(68):
            setattr(dlib_points, 'part', lambda idx=i: points[idx])

        try:
            # 提取特征向量
            features = self.face_encoder.compute_face_descriptor(image, dlib_points)
            return np.array(features)
        except:
            # 如果提取失败，返回模拟特征
            return np.random.rand(128)

    def compare_features(self, features1, features2):
        """
        比较两个特征向量的相似度

        Args:
            features1: 特征向量1
            features2: 特征向量2

        Returns:
            float: 相似度（0-1之间）
        """
        # 欧氏距离计算
        euclidean_distance = np.linalg.norm(features1 - features2)

        # 余弦相似度计算
        dot_product = np.dot(features1, features2)
        norm1 = np.linalg.norm(features1)
        norm2 = np.linalg.norm(features2)
        cosine_similarity = dot_product / (norm1 * norm2)

        # 欧氏距离归一化（转换为相似度）
        # 假设最大距离为4.0（128维特征向量的经验值）
        euclidean_similarity = 1 - min(euclidean_distance / 4.0, 1.0)

        # 融合相似度（欧氏距离40%，余弦相似度60%）
        fused_similarity = 0.4 * euclidean_similarity + 0.6 * cosine_similarity

        # 限制在[0,1]范围内
        fused_similarity = max(0, min(1, fused_similarity))

        return fused_similarity

    def find_best_match(self, query_features, features_db):
        """
        在特征数据库中查找最佳匹配

        Args:
            query_features: 查询特征向量
            features_db: 特征数据库 {student_id: features}

        Returns:
            tuple: (student_id, confidence) 或 (None, 0.0)
        """
        best_match = None
        best_confidence = 0.0

        for student_id, features in features_db.items():
            confidence = self.compare_features(query_features, features)

            if confidence > best_confidence:
                best_confidence = confidence
                best_match = student_id

        return best_match, best_confidence

    def normalize_features(self, features):
        """
        归一化特征向量

        Args:
            features: 特征向量

        Returns:
            numpy.ndarray: 归一化后的特征向量
        """
        norm = np.linalg.norm(features)
        if norm > 0:
            return features / norm
        return features

    def save_features(self, features_db):
        """
        保存特征数据库

        Args:
            features_db: 特征数据库
        """
        import json

        save_data = {}
        for student_id, data in features_db.items():
            save_data[student_id] = {
                'features': data['features'].tolist(),
                'image_path': data.get('image_path', ''),
                'registered_at': data.get('registered_at', '')
            }

        with open(self.features_file, 'w', encoding='utf-8') as f:
            json.dump(save_data, f, ensure_ascii=False)

    def load_features(self):
        """
        加载特征数据库

        Returns:
            dict: 特征数据库
        """
        import json

        if not self.features_file:
            return {}

        try:
            with open(self.features_file, 'r', encoding='utf-8') as f:
                data = json.load(f)

            features_db = {}
            for student_id, info in data.items():
                features_db[student_id] = {
                    'features': np.array(info['features']),
                    'image_path': info.get('image_path', ''),
                    'registered_at': info.get('registered_at', '')
                }

            return features_db
        except FileNotFoundError:
            return {}