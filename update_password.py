import pymysql
import os

# 数据库配置
config = {
    'host': os.environ.get('DB_HOST', 'localhost'),
    'user': os.environ.get('DB_USERNAME', 'root'),
    'password': os.environ.get('DB_PASSWORD', ''),
    'database': os.environ.get('DB_NAME', 'attendance_system'),
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor
}

try:
    connection = pymysql.connect(**config)
    
    with connection.cursor() as cursor:
        # 更新管理员密码
        cursor.execute("UPDATE user SET password = %s WHERE username = %s", 
                      ('$2b$10$IUl32ayOlyDjf0MbPjKztOB.m3Mgtn2G8XeqF1U3RDtX67RPwnVnK', 'admin'))
        
        # 更新其他用户密码
        cursor.execute("UPDATE user SET password = %s WHERE username != %s", 
                      ('$2b$10$AuMjWDuWvQxQGFHGycj/VObi9Zxe8NDxRHuOLMktsrL5mzy12ZkPq', 'admin'))
        
        connection.commit()
        
        # 查询验证
        cursor.execute("SELECT id, username, real_name, role FROM user")
        users = cursor.fetchall()
        print("✅ 密码更新成功！")
        print("\n当前用户列表：")
        for user in users:
            print(f"  ID: {user['id']}, 用户名: {user['username']}, 姓名: {user['real_name']}, 角色: {user['role']}")
        print("\n📝 登录信息：")
        print("  admin / admin123")
        print("  其他所有用户 / password123")
            
    connection.close()
    
except Exception as e:
    print(f"错误: {e}")
