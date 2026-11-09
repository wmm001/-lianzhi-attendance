import io
import unittest
from unittest.mock import patch

import numpy as np
from werkzeug.datastructures import FileStorage

import app_opencv as service


class FaceServiceClassificationTest(unittest.TestCase):
    def setUp(self):
        self.client = service.app.test_client()
        service.face_features_db = {"4": {"features": np.zeros(128, dtype=np.float32)}}

    def post_face(self, require_liveness=None):
        data = {"faceImage": (io.BytesIO(b"image"), "face.jpg")}
        if require_liveness is not None:
            data["requireLiveness"] = str(require_liveness).lower()
        return self.client.post(
            "/face/verify",
            data=data,
            content_type="multipart/form-data",
        )

    def test_quality_error_is_not_reported_as_identity_mismatch(self):
        with patch.object(
            service,
            "extract_face_encoding",
            side_effect=service.FaceQualityError("未检测到人脸"),
        ):
            response = self.post_face()
        self.assertEqual("FACE_QUALITY_INVALID", response.json["errorType"])

    def test_static_single_photo_cannot_pass_liveness(self):
        with patch.object(
            service,
            "extract_face_encoding",
            return_value=(np.zeros(128, dtype=np.float32), {}),
        ):
            response = self.post_face()
        self.assertEqual("LIVENESS_FAILED", response.json["errorType"])

    def test_identity_mismatch_remains_distinct_after_liveness(self):
        with patch.object(service, "extract_face_encoding", return_value=(np.zeros(128), {})), \
             patch.object(service, "validate_liveness_frames", return_value={"passed": True}), \
             patch.object(service.face_recognizer, "find_best_match", return_value=(None, 0.20)):
            response = self.post_face()
        self.assertEqual("FACE_NOT_MATCHED", response.json["errorType"])
        self.assertEqual(0.20, response.json["confidence"])

    def test_quick_mode_skips_liveness_but_still_matches_identity(self):
        with patch.object(service, "extract_face_encoding", return_value=(np.zeros(128), {})), \
             patch.object(service, "validate_liveness_frames") as validate_liveness, \
             patch.object(service.face_recognizer, "find_best_match", return_value=("4", 0.91)):
            response = self.post_face(require_liveness=False)
        self.assertEqual(200, response.status_code)
        self.assertTrue(response.json["success"])
        self.assertEqual("quick", response.json["liveness"]["mode"])
        validate_liveness.assert_not_called()

    def test_enhanced_liveness_tolerates_brief_face_detector_dropouts(self):
        uploads = [object() for _ in range(50)]
        detections = [[object()] for _ in range(32)] + [[] for _ in range(18)]
        with patch.object(service, "decode_uploaded_image", return_value=np.zeros((20, 20, 3), dtype=np.uint8)), \
             patch.object(service.face_detector, "detect_faces_detailed", side_effect=detections), \
             patch.object(service.face_recognizer, "extract_features", return_value=np.zeros(128)), \
             patch.object(service.face_recognizer, "compare_features", return_value=0.8), \
             patch("blink_detection.validate_liveness", return_value={"passed": True}) as validate:
            result = service.validate_liveness_frames(uploads, np.zeros(128))
        self.assertTrue(result["passed"])
        self.assertEqual(32, len(validate.call_args.args[0]))

    def test_enhanced_liveness_rejects_too_few_valid_frames(self):
        uploads = [object() for _ in range(50)]
        detections = [[object()] for _ in range(10)] + [[] for _ in range(40)]
        with patch.object(service, "decode_uploaded_image", return_value=np.zeros((20, 20, 3), dtype=np.uint8)), \
             patch.object(service.face_detector, "detect_faces_detailed", side_effect=detections), \
             patch.object(service.face_recognizer, "extract_features", return_value=np.zeros(128)), \
             patch.object(service.face_recognizer, "compare_features", return_value=0.8), \
             patch("blink_detection.validate_liveness") as validate:
            with self.assertRaisesRegex(service.LivenessError, "有效画面不足"):
                service.validate_liveness_frames(uploads, np.zeros(128))
        validate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
