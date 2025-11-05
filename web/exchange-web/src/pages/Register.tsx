import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { register } from '../api/auth';

export default function Register() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const handleRegister = async (values: {
    email: string;
    password: string;
    confirm: string;
  }) => {
    if (values.password !== values.confirm) {
      message.error('兩次密碼輸入不一致');
      return;
    }

    setLoading(true);
    try {
      const result = await register(values.email, values.password);

      if (result.code === '0') {
        message.success('註冊成功！請登入');
        navigate('/login');
      } else {
        message.error(result.message || '註冊失敗');
      }
    } catch (error: any) {
      message.error(error.response?.data?.message || '註冊失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      background: '#f0f2f5'
    }}>
      <Card title="註冊帳號" style={{ width: 400 }}>
        <Form onFinish={handleRegister}>
          <Form.Item
            name="email"
            rules={[
              { required: true, message: '請輸入信箱' },
              { type: 'email', message: '請輸入有效的信箱' }
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="信箱"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[
              { required: true, message: '請輸入密碼' },
              { min: 8, message: '密碼至少 8 位' }
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密碼"
            />
          </Form.Item>

          <Form.Item
            name="confirm"
            rules={[{ required: true, message: '請確認密碼' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="確認密碼"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
            >
              註冊
            </Button>
          </Form.Item>

          <Button type="link" onClick={() => navigate('/login')} block>
            已有帳號？立即登入
          </Button>
        </Form>
      </Card>
    </div>
  );
}
