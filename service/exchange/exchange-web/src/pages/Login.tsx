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
        localStorage.setItem('refreshAfterLogin', '1');
        message.success('Login successful');
        navigate('/trading');
      } else {
        setLoginError(result.message || 'Login failed');
      }
    } catch (error: any) {
      setLoginError(error.response?.data?.message || 'Login failed');
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
      <Card title="Exchange Login" style={{ width: 400 }}>
        <Form onFinish={handleLogin}>
          {loginError && (
            <Form.Item>
              <Alert message={loginError} type="error" showIcon />
            </Form.Item>
          )}
          <Form.Item
            name="email"
            rules={[{ required: true, message: 'Please enter your email' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="Email"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Please enter your password' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="Password"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              block
            >
              Login
            </Button>
          </Form.Item>

          <Button type="link" onClick={() => navigate('/register')} block>
            Don't have an account? Register now
          </Button>
        </Form>
      </Card>
    </div>
  );
}
