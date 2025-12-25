import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, message, Alert } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login } from '../api/auth';

export default function Login() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);

  const handleLogin = async (values: { email: string; password: string }) => {
    setLoading(true);
    setLoginError(null);
    try {
      const result = await login(values.email, values.password);

      if (result.code === '0') {
        // 儲存 Token
        const accessToken = result.data?.jwtToken || result.data?.token;
        if (accessToken) {
          localStorage.setItem('accessToken', accessToken);
        }
        message.success('登入成功！');
        navigate('/trading');
      } else {
        setLoginError(result.message || '登入失敗');
      }
    } catch (error: any) {
      setLoginError(error.response?.data?.message || '登入失敗');
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
      background: 'transparent'
    }}>
      <Card title="交易所登入" style={{ width: 400 }}>
        <Form onFinish={handleLogin}>
          {loginError && (
            <Form.Item>
              <Alert message={loginError} type="error" showIcon />
            </Form.Item>
          )}
          <Form.Item
            name="email"
            rules={[{ required: true, message: '請輸入信箱' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="信箱"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '請輸入密碼' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密碼"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
            >
              登入
            </Button>
          </Form.Item>

          <Button type="link" onClick={() => navigate('/register')} block>
            沒有帳號？立即註冊
          </Button>
        </Form>
      </Card>
    </div>
  );
}
