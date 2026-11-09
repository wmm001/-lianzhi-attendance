"""
活体检测模块
结合EAR眨眼检测和鼻尖摇头追踪
"""

import numpy as np


class LivenessDetector:
    """活体检测器"""

    def __init__(self, ear_threshold=0.25, movement_threshold=50):
        self.ear_threshold = ear_threshold
        self.movement_threshold = movement_threshold

        # 存储最近几帧的眼部EAR值
        self.ear_history = []
        self.max_ear_history = 30  # 保存30帧

        # 存储鼻尖位置
        self.nose_history = []
        self.max_nose_history = 30

    def calculate_ear(self, eye_landmarks):
        """
        计算眼部纵横比（Eye Aspect Ratio）
        用于眨眼检测

        Args:
            eye_landmarks: 眼部关键点（6个点）

        Returns:
            float: EAR值
        """
        # 垂直距离
        A = np.linalg.norm(eye_landmarks[1] - eye_landmarks[5])
        B = np.linalg.norm(eye_landmarks[2] - eye_landmarks[4])

        # 水平距离
        C = np.linalg.norm(eye_landmarks[0] - eye_landmarks[3])

        # EAR计算
        ear = (A + B) / (2.0 * C)

        return ear

    def detect_blink(self, landmarks):
        """
        检测眨眼

        Args:
            landmarks: 68个关键点

        Returns:
            bool: 是否检测到眨眼
        """
        # 获取眼部关键点
        left_eye = landmarks[36:42]
        right_eye = landmarks[42:48]

        # 计算左右眼的EAR
        left_ear = self.calculate_ear(left_eye)
        right_ear = self.calculate_ear(right_eye)

        # 平均EAR
        avg_ear = (left_ear + right_ear) / 2.0

        # 保存到历史记录
        self.ear_history.append(avg_ear)
        if len(self.ear_history) > self.max_ear_history:
            self.ear_history.pop(0)

        # 检查是否有眨眼（EAR低于阈值）
        if len(self.ear_history) < 3:
            return False

        # 检查是否有连续几帧的EAR值低于阈值
        blink_frames = sum(1 for ear in self.ear_history[-10:] if ear < self.ear_threshold)

        return blink_frames >= 3  # 至少3帧低于阈值认为眨眼

    def detect_head_movement(self, landmarks):
        """
        检测头部摇动（鼻尖移动）

        Args:
            landmarks: 68个关键点

        Returns:
            bool: 是否检测到有效摇动
        """
        # 获取鼻尖位置（第30个点）
        nose_tip = landmarks[30]

        # 保存到历史记录
        self.nose_history.append(nose_tip)
        if len(self.nose_history) > self.max_nose_history:
            self.nose_history.pop(0)

        # 至少需要3帧才能检测移动
        if len(self.nose_history) < 3:
            return False

        # 计算鼻尖移动距离
        distances = []
        for i in range(1, len(self.nose_history)):
            distance = np.linalg.norm(self.nose_history[i] - self.nose_history[i-1])
            distances.append(distance)

        # 计算最大移动距离和平均移动距离
        max_distance = max(distances) if distances else 0
        avg_distance = np.mean(distances) if distances else 0

        # 判断是否有有效摇动
        return max_distance > self.movement_threshold and avg_distance > 20

    def detect(self, image, landmarks):
        """
        综合活体检测

        Args:
            image: 图像（暂未使用，可用于多帧分析）
            landmarks: 68个关键点

        Returns:
            bool: 是否通过活体检测
        """
        # 检测眨眼
        blink_detected = self.detect_blink(landmarks)

        # 检测头部摇动
        movement_detected = self.detect_head_movement(landmarks)

        # 综合判断：需要眨眼或摇动（二选一）
        # 在实际应用中，可以要求两者都检测到
        return blink_detected or movement_detected

    def reset(self):
        """
        重置历史记录
        """
        self.ear_history.clear()
        self.nose_history.clear()

    def get_ear_stats(self):
        """
        获取EAR统计信息

        Returns:
            dict: EAR统计信息
        """
        if not self.ear_history:
            return {
                'current': 0.0,
                'min': 0.0,
                'max': 0.0,
                'avg': 0.0
            }

        return {
            'current': self.ear_history[-1],
            'min': min(self.ear_history),
            'max': max(self.ear_history),
            'avg': np.mean(self.ear_history)
        }

    def get_nose_movement_stats(self):
        """
        获取鼻尖移动统计信息

        Returns:
            dict: 移动统计信息
        """
        if len(self.nose_history) < 2:
            return {
                'total_distance': 0.0,
                'max_distance': 0.0,
                'avg_distance': 0.0
            }

        distances = []
        total = 0.0
        for i in range(1, len(self.nose_history)):
            distance = np.linalg.norm(self.nose_history[i] - self.nose_history[i-1])
            distances.append(distance)
            total += distance

        return {
            'total_distance': total,
            'max_distance': max(distances),
            'avg_distance': np.mean(distances)
        }


# 测试代码
if __name__ == '__main__':
    # 创建测试关键点
    landmarks = np.random.randint(100, 300, (68, 2))

    # 创建活体检测器
    detector = LivenessDetector()

    # 模拟30帧的检测
    for i in range(30):
        # 随机移动关键点
        movement = np.random.randint(-5, 5, (68, 2))
        landmarks = landmarks + movement

        # 检测活体
        is_live = detector.detect(None, landmarks)
        print(f"Frame {i+1}: Liveness = {is_live}")

        # 打印统计信息
        if i % 10 == 9:
            print(f"EAR Stats: {detector.get_ear_stats()}")
            print(f"Nose Movement: {detector.get_nose_movement_stats()}")