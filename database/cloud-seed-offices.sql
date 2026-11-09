USE lianzhi_attendance;

INSERT INTO office(code,company_name,name,address,latitude,longitude,status)
SELECT 'HQ','郑州轻工业大学','郑州轻工业大学东风校区','河南省郑州市金水区东风路5号（东一楼）',34.796731,113.656074,1
WHERE NOT EXISTS (SELECT 1 FROM office WHERE code='HQ');
INSERT INTO office(code,company_name,name,address,latitude,longitude,status)
SELECT 'BRANCH','郑州轻工业大学','郑州轻工业大学学生第二生活园区','河南省郑州市金水区东风路与勤工路交叉口西南50米（5号楼）',34.798712,113.660983,1
WHERE NOT EXISTS (SELECT 1 FROM office WHERE code='BRANCH');
