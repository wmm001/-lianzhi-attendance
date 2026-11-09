import pymysql
import os

# 读取SQL文件
def read_sql_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        return f.read()

# 执行SQL语句
def execute_sql(connection, sql):
    with connection.cursor() as cursor:
        # 分割SQL语句（按分号）
        statements = [s.strip() for s in sql.split(';') if s.strip()]
        for statement in statements:
            try:
                cursor.execute(statement)
            except Exception as e:
                print(f"执行SQL时出错: {e}")
                print(f"SQL: {statement[:100]}...")
    connection.commit()

def main():
    # 数据库配置
    config = {
        'host': os.environ.get('DB_HOST', 'localhost'),
        'user': os.environ.get('DB_USERNAME', 'root'),
        'password': os.environ.get('DB_PASSWORD', ''),
        'charset': 'utf8mb4',
        'cursorclass': pymysql.cursors.DictCursor
    }
    
    try:
        # 连接MySQL（不指定数据库）
        connection = pymysql.connect(**config)
        print("成功连接到MySQL服务器！")
        
        # 读取并执行schema.sql
        schema_sql = read_sql_file('database/schema.sql')
        print("正在执行数据库初始化...")
        execute_sql(connection, schema_sql)
        print("数据库结构创建完成！")
        
        # 重新连接到attendance_system数据库
        connection.close()
        config['database'] = os.environ.get('DB_NAME', 'attendance_system')
        connection = pymysql.connect(**config)
        
        # 读取并执行init-data.sql
        init_sql = read_sql_file('database/init-data.sql')
        print("正在插入初始数据...")
        execute_sql(connection, init_sql)
        print("初始数据插入完成！")
        
        print("\n🎉 数据库初始化成功！")
        
        connection.close()
        
    except Exception as e:
        print(f"错误: {e}")

if __name__ == '__main__':
    main()
