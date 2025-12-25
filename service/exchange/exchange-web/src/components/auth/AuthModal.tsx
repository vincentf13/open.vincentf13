import { useState } from 'react';
import { Alert, Button, Form, Input, Modal, Tabs, message } from 'antd';

import { login, register } from '../../api/auth';

type AuthModalProps = {
  open: boolean;
  onSuccess: () => void;
};

const resolveToken = (payload: any) => {
  return payload?.jwtToken || payload?.token || null;
};

export default function AuthModal({ open, onSuccess }: AuthModalProps) {
  const [activeTab, setActiveTab] = useState('login');
  const [loginLoading, setLoginLoading] = useState(false);
  const [registerLoading, setRegisterLoading] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [registerError, setRegisterError] = useState<string | null>(null);
  const [loginForm] = Form.useForm();
  const [registerForm] = Form.useForm();

  const handleLogin = async (values: { email: string; password: string }) => {
    setLoginLoading(true);
    setLoginError(null);
    try {
      const result = await login(values.email, values.password);
      if (String(result?.code) === '0') {
        const token = resolveToken(result?.data);
        if (token) {
          localStorage.setItem('accessToken', token);
        }
        message.success('登入成功');
        onSuccess();
      } else {
        setLoginError(result?.message || '登入失敗');
      }
    } catch (error: any) {
      setLoginError(error?.response?.data?.message || '登入失敗');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleRegister = async (values: { email: string; password: string }) => {
    setRegisterLoading(true);
    setRegisterError(null);
    try {
      const result = await register(values.email, values.password);
      if (String(result?.code) === '0') {
        message.success('註冊成功，請登入');
        registerForm.resetFields();
        setActiveTab('login');
      } else {
        setRegisterError(result?.message || '註冊失敗');
      }
    } catch (error: any) {
      setRegisterError(error?.response?.data?.message || '註冊失敗');
    } finally {
      setRegisterLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      title="登入 / 註冊"
      footer={null}
      closable={false}
      maskClosable={false}
      centered
    >
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'login',
            label: '登入',
            children: (
              <Form form={loginForm} layout="vertical" onFinish={handleLogin}>
                {loginError && (
                  <Form.Item>
                    <Alert message={loginError} type="error" showIcon />
                  </Form.Item>
                )}
                <Form.Item name="email" rules={[{ required: true, message: '請輸入信箱' }]}>
                  <Input placeholder="信箱" />
                </Form.Item>
                <Form.Item name="password" rules={[{ required: true, message: '請輸入密碼' }]}>
                  <Input.Password placeholder="密碼" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={loginLoading} block>
                  登入
                </Button>
              </Form>
            ),
          },
          {
            key: 'register',
            label: '註冊',
            children: (
              <Form form={registerForm} layout="vertical" onFinish={handleRegister}>
                {registerError && (
                  <Form.Item>
                    <Alert message={registerError} type="error" showIcon />
                  </Form.Item>
                )}
                <Form.Item name="email" rules={[{ required: true, message: '請輸入信箱' }]}>
                  <Input placeholder="信箱" />
                </Form.Item>
                <Form.Item name="password" rules={[{ required: true, message: '請輸入密碼' }]}>
                  <Input.Password placeholder="密碼" />
                </Form.Item>
                <Button type="primary" htmlType="submit" loading={registerLoading} block>
                  註冊
                </Button>
              </Form>
            ),
          },
        ]}
      />
    </Modal>
  );
}
