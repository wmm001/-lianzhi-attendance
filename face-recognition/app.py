import os
import json
import numpy as np
from flask import Flask, request, jsonify
from flask_cors import CORS
from werkzeug.utils import secure_filename
from datetime import datetime
import face_recognition

app = Flask(__name__)
CORS(app)

# 配置
UPLOAD_FOLDER = 'uploads'
FEATURES_FILE = 'face_features.json'
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg'}
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
TOLERANCE = 0.6  # 人脸匹配阈值（越小越严格）

# 创建上传目录
os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs('models', exist_ok=True)

# 人脸特征数据库（从文件加载）
face_features_db = {}

def load_features():
    """从文件加载人脸特征"""
    global face_features_db
    if os.path.exists(FEATURES_FILE):
        try:
            with open(FEATURES_FILE, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # 转换字符串列表回 numpy 数组
                for student_id, info in data.items():
                    info['features'] = np.array(info['features'])
                face_features_db = data
            print(f"✅ 已加载 {len(face_features_db)} 个人脸特征")
        except Exception as e:
            print(f"⚠️  加载特征文件失败: {e}")
            face_features_db = {}

def save_features():
    """保存人脸特征到文件"""
    try:
        data = {}
        for student_id, info in face_features_db.items():
            data[student_id] = {
                'features': info['features'].tolist() if isinstance(info['features'], np.ndarray) else info['features'],
                'image_path': info['image_path'],
                'created_at': info['created_at']
            }
        with open(FEATURES_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"✅ 已保存 {len(face_features_db)} 个人脸特征")
    except Exception as e:
        print(f"⚠️  保存特征文件失败: {e}")

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def extract_face_encoding(image_path):
    """从图片中提取人脸特征"""
    try:
        image = face_recognition.load_image_file(image_path)
        face_locations = face_recognition.face_locations(image)

        if len(face_locations) == 0:
            print(f"⚠️  图片中没有检测到人脸: {image_path}")
            return None

        if len(face_locations) > 1:
            print(f"⚠️  图片中检测到多个人脸，使用第一个: {image_path}")

        # 使用第一张人脸提取特征
        face_encoding = face_recognition.face_encodings(image, face_locations)[0]
        return face_encoding
    except Exception as e:
        print(f"❌ 提取人脸特征失败: {e}")
        return None

@app.route('/health', methods=['GET'])
def health():
    return jsonify({
        'status': 'ok',
        'message': '人脸识别服务运行正常',
        'registered_count': len(face_features_db)
    })

@app.route('/face/register', methods=['POST'])
def register_face():
    if 'faceImage' not in request.files:
        return jsonify({'success': False, 'message': '没有上传图片'}), 400

    file = request.files['faceImage']
    student_id = request.form.get('studentId')

    if not student_id:
        return jsonify({'success': False, 'message': '缺少学生ID'}), 400

    if file.filename == '':
        return jsonify({'success': False, 'message': '没有选择文件'}), 400

    if file and allowed_file(file.filename):
        # 保存文件
        filename = secure_filename(f"{student_id}_{datetime.now().strftime('%Y%m%d%H%M%S')}.jpg")
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], filename)
        file.save(filepath)
        print(f"📸 图片已保存: {filepath}")

        # 提取人脸特征
        encoding = extract_face_encoding(filepath)
        if encoding is None:
            # 删除无效图片
            os.remove(filepath)
            return jsonify({'success': False, 'message': '未检测到人脸，请使用清晰的正面照片'}), 400

        # 存储特征
        face_features_db[str(student_id)] = {
            'features': encoding,
            'image_path': filepath,
            'created_at': datetime.now().isoformat()
        }

        # 保存到文件
        save_features()

        print(f"✅ 学生 {student_id} 人脸注册成功")
        return jsonify({
            'success': True,
            'message': '人脸注册成功',
            'studentId': student_id
        })

    return jsonify({'success': False, 'message': '文件格式不支持'}), 400

@app.route('/face/verify', methods=['POST'])
def verify_face():
    if 'faceImage' not in request.files:
        return jsonify({'success': False, 'message': '没有上传图片'}), 400

    file = request.files['faceImage']

    if not file or not allowed_file(file.filename):
        return jsonify({'success': False, 'message': '文件格式不支持'}), 400

    if len(face_features_db) == 0:
        return jsonify({'success': False, 'message': '暂无注册人脸数据'}), 400

    # 保存临时文件
    temp_path = os.path.join(UPLOAD_FOLDER, f"temp_{datetime.now().timestamp()}.jpg")
    file.save(temp_path)

    try:
        # 提取待识别的人脸特征
        unknown_encoding = extract_face_encoding(temp_path)
        if unknown_encoding is None:
            return jsonify({'success': False, 'message': '未检测到人脸'}), 400

        # 与已注册的人脸比对
        known_face_ids = list(face_features_db.keys())
        known_face_encodings = [face_features_db[sid]['features'] for sid in known_face_ids]

        # 获取匹配结果和距离
        face_distances = face_recognition.face_distance(known_face_encodings, unknown_encoding)
        best_match_index = np.argmin(face_distances)

        if face_distances[best_match_index] <= TOLERANCE:
            matched_student_id = known_face_ids[best_match_index]
            confidence = 1.0 - face_distances[best_match_index]  # 转换为置信度

            print(f"✅ 人脸识别成功: 学生ID={matched_student_id}, 距离={face_distances[best_match_index]:.4f}, 置信度={confidence:.4f}")

            return jsonify({
                'success': True,
                'message': '人脸识别成功',
                'studentId': matched_student_id,
                'confidence': round(confidence, 4)
            })
        else:
            print(f"❌ 人脸识别失败: 最小距离={face_distances[best_match_index]:.4f} > 阈值={TOLERANCE}")
            return jsonify({
                'success': False,
                'message': '未匹配到已注册的人脸'
            }), 400

    except Exception as e:
        print(f"❌ 人脸识别异常: {e}")
        return jsonify({'success': False, 'message': '识别服务异常'}), 500
    finally:
        # 删除临时文件
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
        # 删除图片文件
        image_path = face_features_db[student_id]['image_path']
        if os.path.exists(image_path):
            os.remove(image_path)

        del face_features_db[student_id]
        save_features()
        print(f"✅ 学生 {student_id} 人脸已删除")

    return jsonify({
        'success': True,
        'message': '人脸删除成功'
    })

@app.route('/face/list', methods=['GET'])
def list_faces():
    """列出所有已注册的人脸"""
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
    print("🚀 人脸识别服务启动中...")
    print("📌 服务地址: http://localhost:5000")
    print("📁 上传目录:", UPLOAD_FOLDER)
    print("💾 特征文件:", FEATURES_FILE)

    # 启动时加载已保存的人脸特征
    load_features()

    app.run(host='0.0.0.0', port=5000, debug=True)