"""
Face recognition service (using OpenCV instead of dlib)
"""
import os
import json
import numpy as np
import cv2
import traceback
from flask import Flask, request, jsonify, render_template_string
from werkzeug.utils import secure_filename
from datetime import datetime

# Import OpenCV-based modules
from face_detection_opencv import FaceDetector, FaceQualityError
from face_recognition_opencv import FaceRecognizer

app = Flask(__name__)

# Configuration
UPLOAD_FOLDER = os.getenv('FACE_UPLOAD_FOLDER', 'uploads')
FEATURES_FILE = os.getenv('FACE_FEATURES_FILE', 'face_features.json')
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg'}
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
TOLERANCE = 0.45  # SFace cosine threshold; stricter than OpenCV's 0.363 reference threshold

# Create directories
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs('models', exist_ok=True)

# Initialize detector and recognizer
face_detector = FaceDetector()
face_recognizer = FaceRecognizer(features_file=FEATURES_FILE)

# Face features database
face_features_db = {}

class LivenessError(ValueError):
    pass

SERVICE_PAGE = r'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>人脸识别服务</title><style>
*{box-sizing:border-box}body{margin:0;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;background:#f5f7fb;color:#172033;min-height:100vh;display:grid;place-items:center}.shell{width:min(920px,calc(100% - 32px))}.top{display:flex;align-items:center;gap:14px;margin-bottom:24px}.logo{width:48px;height:48px;border-radius:14px;background:#2563eb;color:#fff;display:grid;place-items:center;font-size:23px}.top h1{font-size:24px;margin:0}.top p{margin:4px 0 0;color:#667085;font-size:14px}.card{background:#fff;border:1px solid #e7eaf0;border-radius:18px;padding:34px;box-shadow:0 12px 34px rgba(16,24,40,.07)}.status{display:flex;justify-content:space-between;align-items:center;padding-bottom:28px;border-bottom:1px solid #eef0f4}.badge{display:inline-flex;align-items:center;gap:8px;background:#ecfdf3;color:#027a48;border:1px solid #abefc6;padding:8px 12px;border-radius:999px;font-size:13px;font-weight:600}.dot{width:8px;height:8px;background:#12b76a;border-radius:50%}.count strong{font-size:28px}.count span{display:block;color:#98a2b3;font-size:12px;margin-top:2px}.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:28px}.item{padding:18px;border:1px solid #e7eaf0;border-radius:12px;background:#fafbfc}.item b{display:block;font-size:14px;margin-bottom:8px}.item code{color:#2563eb;font-size:12px}.foot{color:#98a2b3;font-size:12px;margin-top:22px}@media(max-width:650px){.grid{grid-template-columns:1fr}.status{align-items:flex-start;gap:20px}.card{padding:24px}}
</style></head><body><main class="shell"><header class="top"><div class="logo">◎</div><div><h1>人脸识别服务</h1><p>OpenCV Face Recognition Service</p></div></header><section class="card"><div class="status"><div><span class="badge"><i class="dot"></i>服务运行正常</span></div><div class="count"><strong>{{ count }}</strong><span>已注册人脸</span></div></div><div class="grid"><div class="item"><b>健康检查</b><code>GET /health</code></div><div class="item"><b>人脸注册</b><code>POST /face/register</code></div><div class="item"><b>人脸验证</b><code>POST /face/verify</code></div></div><p class="foot">该页面用于显示底层识别服务状态。请通过人脸考勤系统前端进行注册和签到操作。</p></section></main></body></html>'''

def load_features():
    """Load face features from file"""
    global face_features_db
    face_features_db = face_recognizer.load_features()


# Gunicorn imports this module without entering __main__.
load_features()

def save_features():
    """Save face features to file"""
    face_recognizer.save_features(face_features_db)

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def error_response(error_type, message, status=400, **details):
    """Return a stable machine-readable reason without losing the human explanation."""
    payload = {
        'success': False,
        'errorType': error_type,
        'message': message
    }
    payload.update(details)
    return jsonify(payload), status

@app.route('/', methods=['GET'])
def service_home():
    return render_template_string(SERVICE_PAGE, count=len(face_features_db))

def extract_face_encoding(image_path):
    """Validate one clear front face, align it, and extract an SFace embedding."""
    image = cv2.imread(image_path)
    if image is None:
        raise FaceQualityError("无法读取照片，请重新选择 JPG 或 PNG 图片")
    face, quality = face_detector.validate_single_front_face(image)
    encoding = face_recognizer.extract_features(image, face)
    return encoding, quality

def decode_uploaded_image(upload):
    raw = upload.read()
    upload.stream.seek(0)
    return cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)

def validate_liveness_frames(uploads, reference_encoding):
    from blink_detection import validate_liveness
    if not 8 <= len(uploads) <= 50:
        raise LivenessError('需要8至50帧连续摄像头画面，请正对镜头眨眼或张嘴一次')
    images = []
    identity_mismatch = 0
    for upload in uploads:
        image = decode_uploaded_image(upload)
        if image is None:
            raise LivenessError('摄像头画面无效，请重试')
        faces = face_detector.detect_faces_detailed(image)
        if len(faces) > 1:
            raise LivenessError('画面中只能有本人一张人脸')
        if not faces:
            # A brief profile turn can make the frontal detector miss a frame.
            # Keep enough matched frames for the full blink and turn sequence.
            continue
        feature = face_recognizer.extract_features(image, faces[0])
        if face_recognizer.compare_features(reference_encoding, feature) < 0.28:
            identity_mismatch += 1
            continue
        images.append(image)
    if identity_mismatch > 10:
        raise LivenessError('连续画面中的人脸不一致，请本人完成动作')
    if len(images) < max(20, int(len(uploads) * 0.55)):
        raise LivenessError('有效画面不足，请稍微靠近镜头，缓慢转头并保持脸部在画面内')
    try:
        return validate_liveness(images)
    except (ImportError, FileNotFoundError, RuntimeError) as error:
        raise LivenessError('活体检测模型暂不可用，请联系管理员检查服务') from error
    except ValueError as error:
        raise LivenessError(str(error)) from error

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'message': 'Face recognition service running (OpenCV)',
        'registered_count': len(face_features_db)
    })


@app.route('/face/liveness/step', methods=['POST'])
def verify_liveness_step():
    step = request.form.get('step', '').strip()
    if step not in {'align', 'blink', 'turn_left', 'turn_right'}:
        return error_response('INVALID_LIVENESS_STEP', '无效的活体检测步骤')
    uploads = request.files.getlist('frames')
    if not 1 <= len(uploads) <= 50:
        return error_response('LIVENESS_STEP_FAILED', '该步骤没有采集到有效画面', step=step)
    try:
        images = []
        for upload in uploads:
            image = decode_uploaded_image(upload)
            if image is None:
                raise ValueError('摄像头画面无效，请重新检测')
            if step == 'align':
                face_detector.validate_single_front_face(image)
            else:
                faces = face_detector.detect_faces_detailed(image)
                if len(faces) != 1:
                    raise ValueError('该步骤没有正确检测到单人脸，请保持脸部在框内')
            images.append(image)
        if step == 'align':
            result = {'passed': True, 'step': step, 'validFrames': len(images)}
        else:
            from blink_detection import validate_liveness_step
            result = validate_liveness_step(images, step)
        return jsonify({'success': True, **result})
    except (ImportError, FileNotFoundError, RuntimeError):
        return error_response('LIVENESS_MODEL_UNAVAILABLE', '活体检测模型暂不可用，请联系管理员', step=step, status=503)
    except (FaceQualityError, ValueError) as error:
        return error_response('LIVENESS_STEP_FAILED', str(error), step=step)

@app.route('/face/register', methods=['POST'])
def register_face():
    if 'faceImage' not in request.files:
        return jsonify({'success': False, 'message': 'No image uploaded'}), 400

    file = request.files['faceImage']
    student_id = request.form.get('studentId')

    if not student_id:
        return jsonify({'success': False, 'message': 'Missing student ID'}), 400

    if file.filename == '':
        return jsonify({'success': False, 'message': 'No file selected'}), 400

    if file and allowed_file(file.filename):
        # Save file
        filename = secure_filename(f"{student_id}_{datetime.now().strftime('%Y%m%d%H%M%S')}.jpg")
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(filepath)
        print(f"Image saved: {filepath}")

        # Extract face features
        try:
            encoding, quality = extract_face_encoding(filepath)
        except FaceQualityError as error:
            os.remove(filepath)
            return error_response(
                'FACE_QUALITY_INVALID',
                f'照片不符合拍摄要求：{error}'
            )
        except Exception as error:
            os.remove(filepath)
            print(f"[ERROR] SFace registration failed: {error}")
            return error_response(
                'FACE_PROCESSING_FAILED',
                '照片不符合拍摄要求：无法提取有效人脸特征，请重新拍摄清晰正脸'
            )

        # Store features
        face_features_db[str(student_id)] = {
            'features': encoding,
            'image_path': '',
            'created_at': datetime.now().isoformat(),
            'quality': quality
        }

        # Save to file
        save_features()

        # Only the numerical embedding is retained. The original biometric photo is not stored.
        if os.path.exists(filepath):
            os.remove(filepath)

        print(f"[OK] Student {student_id} face registered successfully")
        return jsonify({
            'success': True,
            'message': '人脸注册成功',
            'studentId': student_id,
            'quality': quality
        })

    return jsonify({'success': False, 'message': 'Unsupported file format'}), 400

@app.route('/face/verify', methods=['POST'])
def verify_face():
    if 'faceImage' not in request.files:
        return jsonify({'success': False, 'message': 'No image uploaded'}), 400

    file = request.files['faceImage']

    if not file or not allowed_file(file.filename):
        return jsonify({'success': False, 'message': 'Unsupported file format'}), 400

    if len(face_features_db) == 0:
        return error_response('NO_REGISTERED_FACE', '当前账号尚未注册人脸，请先完成人脸注册')

    # Save temporary file
    temp_path = os.path.join(UPLOAD_FOLDER, f"temp_{datetime.now().timestamp()}.jpg")
    file.save(temp_path)

    try:
        # Extract face features to verify
        unknown_encoding, quality = extract_face_encoding(temp_path)
        require_liveness = request.form.get('requireLiveness', 'true').lower() == 'true'
        liveness = (validate_liveness_frames(request.files.getlist('livenessFrames'), unknown_encoding)
                    if require_liveness else {'passed': True, 'mode': 'quick'})

        # Prepare feature database
        features_db = {sid: info['features'] for sid, info in face_features_db.items()}

        # Find best match
        matched_student_id, confidence = face_recognizer.find_best_match(
            unknown_encoding, features_db, threshold=TOLERANCE
        )

        if matched_student_id:
            print(f"[OK] Face recognition successful: StudentID={matched_student_id}, Confidence={confidence:.4f}")

            return jsonify({
                'success': True,
                'message': '人脸识别成功',
                'studentId': matched_student_id,
                'confidence': round(float(confidence), 4),
                'quality': quality,
                'liveness': liveness
            })
        else:
            print(f"[FAILED] Face recognition failed: No match found, Confidence={confidence:.4f}")
            return error_response(
                'FACE_NOT_MATCHED',
                '身份核验失败：检测到的是非注册人脸，与当前账号已注册人脸不匹配',
                confidence=round(float(confidence), 4),
                threshold=TOLERANCE
            )

    except FaceQualityError as e:
        return error_response(
            'FACE_QUALITY_INVALID',
            f'照片不符合拍摄要求：{e}'
        )
    except LivenessError as e:
        return error_response('LIVENESS_FAILED', f'活体检测未通过：{e}')
    except Exception as e:
        print(f"[ERROR] Face recognition exception: {e}", flush=True)
        traceback.print_exc()
        return error_response(
            'FACE_PROCESSING_FAILED',
            '照片不符合拍摄要求：无法完成人脸特征提取，请重新拍摄清晰正脸'
        )
    finally:
        # Delete temporary file
        if os.path.exists(temp_path):
            os.remove(temp_path)

@app.route('/face/features/<student_id>', methods=['GET'])
def get_features(student_id):
    if student_id in face_features_db:
        return jsonify({
            'success': True,
            'studentId': student_id,
            'registered': True,
            'image_path': face_features_db[student_id]['image_path'],
            'created_at': face_features_db[student_id]['created_at']
        })
    else:
        return jsonify({
            'success': True,
            'studentId': student_id,
            'registered': False
        })

@app.route('/face/<student_id>', methods=['DELETE'])
def delete_face(student_id):
    if student_id in face_features_db:
        # Delete image file
        image_path = face_features_db[student_id]['image_path']
        if os.path.exists(image_path):
            os.remove(image_path)

        del face_features_db[student_id]
        save_features()
        print(f"[OK] Student {student_id} face deleted")

    return jsonify({
        'success': True,
        'message': 'Face deleted successfully'
    })

@app.route('/face/list', methods=['GET'])
def list_faces():
    """List all registered faces"""
    faces = []
    for student_id, info in face_features_db.items():
        faces.append({
            'studentId': student_id,
            'image_path': info['image_path'],
            'created_at': info['created_at']
        })
    return jsonify({
        'success': True,
        'count': len(faces),
        'faces': faces
    })

if __name__ == '__main__':
    print("Face recognition service starting (using OpenCV)...")
    print("Service URL: http://localhost:5000")
    print("Upload directory:", UPLOAD_FOLDER)
    print("Features file:", FEATURES_FILE)

    app.run(
        host=os.getenv('FACE_SERVICE_HOST', '127.0.0.1'),
        port=int(os.getenv('FACE_SERVICE_PORT', '5000')),
        debug=os.getenv('FLASK_DEBUG', '0') == '1'
    )
