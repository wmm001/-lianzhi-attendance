"""SFace identity embeddings and cosine matching."""
import json
import os
import shutil
import tempfile
import cv2
import numpy as np


class FaceRecognizer:
    MODEL_VERSION = "opencv_sface_2021dec_v1"

    def __init__(self, features_file=None, model_path=None):
        base = os.path.dirname(os.path.abspath(__file__))
        model_path = model_path or os.path.join(base, "models", "face_recognition_sface_2021dec.onnx")
        if not os.path.exists(model_path):
            raise RuntimeError("SFace model is missing: " + model_path)
        try:
            model_path.encode("ascii")
        except UnicodeEncodeError:
            safe_dir = os.path.join(tempfile.gettempdir(), "attendance-face-models")
            os.makedirs(safe_dir, exist_ok=True)
            safe_path = os.path.join(safe_dir, os.path.basename(model_path))
            if not os.path.exists(safe_path) or os.path.getsize(safe_path) != os.path.getsize(model_path):
                shutil.copyfile(model_path, safe_path)
            model_path = safe_path
        self.features_file = features_file
        self.recognizer = cv2.FaceRecognizerSF.create(model_path, "")
        print("[OK] SFace recognizer loaded")

    def extract_features(self, image, face):
        aligned = self.recognizer.alignCrop(image, np.asarray(face, dtype=np.float32))
        feature = self.recognizer.feature(aligned).flatten().astype(np.float32)
        norm = np.linalg.norm(feature)
        if norm == 0 or not np.isfinite(norm):
            raise ValueError("无法提取有效的人脸特征")
        return feature / norm

    @staticmethod
    def compare_features(first, second):
        first = np.asarray(first, dtype=np.float32).reshape(-1)
        second = np.asarray(second, dtype=np.float32).reshape(-1)
        if first.size != 128 or second.size != 128:
            return 0.0
        denominator = np.linalg.norm(first) * np.linalg.norm(second)
        return 0.0 if denominator == 0 else float(np.dot(first, second) / denominator)

    def find_best_match(self, query_features, features_db, threshold=0.45):
        best_id, best_score = None, -1.0
        for student_id, features in features_db.items():
            score = self.compare_features(query_features, features)
            if score > best_score:
                best_id, best_score = student_id, score
        return (best_id, best_score) if best_id is not None and best_score >= threshold else (None, max(best_score, 0.0))

    def save_features(self, features_db):
        if not self.features_file:
            return
        output = {}
        for student_id, data in features_db.items():
            output[str(student_id)] = {
                "model": self.MODEL_VERSION,
                "features": np.asarray(data["features"], dtype=np.float32).reshape(-1).tolist(),
                "image_path": data.get("image_path", ""),
                "created_at": data.get("created_at", "")
            }
        with open(self.features_file, "w", encoding="utf-8") as handle:
            json.dump(output, handle, ensure_ascii=False)
        print("[OK] SFace feature database saved")

    def load_features(self):
        if not self.features_file or not os.path.exists(self.features_file):
            return {}
        try:
            with open(self.features_file, "r", encoding="utf-8") as handle:
                source = json.load(handle)
            result = {}
            for student_id, info in source.items():
                features = np.asarray(info.get("features", []), dtype=np.float32).reshape(-1)
                if info.get("model") != self.MODEL_VERSION or features.size != 128:
                    print(f"[MIGRATION] Ignoring legacy feature for student {student_id}; re-registration required")
                    continue
                result[str(student_id)] = {"features": features, "image_path": info.get("image_path", ""), "created_at": info.get("created_at", "")}
            print(f"[OK] Loaded {len(result)} SFace features")
            return result
        except Exception as error:
            print(f"[WARNING] Failed to load SFace database: {error}")
            return {}
