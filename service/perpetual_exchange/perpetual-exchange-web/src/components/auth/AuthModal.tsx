import {useEffect, useState} from 'react';
import {Alert, Button, Form, Input, message, Modal, Tabs} from 'antd';

import {login, register} from '../../api/auth';

type AuthModalProps = {
    open: boolean;
    onSuccess: () => void;
};

const resolveToken = (payload: any) => {
    return payload?.jwtToken || payload?.token || null;
};

export default function AuthModal({open, onSuccess}: AuthModalProps) {
    const [activeTab, setActiveTab] = useState('login');
    const [loginLoading, setLoginLoading] = useState(false);
    const [registerLoading, setRegisterLoading] = useState(false);
    const [loginError, setLoginError] = useState<string | null>(null);
    const [registerError, setRegisterError] = useState<string | null>(null);
    const [loginForm] = Form.useForm();
    const [registerForm] = Form.useForm();

    useEffect(() => {
        if (open) {
            setLoginError(null);
            setRegisterError(null);
            loginForm.resetFields();
            registerForm.resetFields();
            setActiveTab('login');
        }
    }, [open, loginForm, registerForm]);

    const handleLogin = async (values: { email: string; password: string }) => {
        if (loginLoading) return;
        setLoginLoading(true);
        setLoginError(null);
        try {
            const result = await login(values.email, values.password);
            if (String(result?.code) === '0') {
                const token = resolveToken(result?.data);
                if (token) {
                    localStorage.setItem('accessToken', token);
                }
                message.success('Login successful');
                onSuccess();
            } else {
                console.error('Login API error:', result);
                setLoginError(result?.message || `Login failed (Code: ${result?.code})`);
            }
        } catch (error: any) {
            console.error('Login exception:', error);
            const msg = error?.response?.data?.message || error?.message || 'Login failed';
            setLoginError(msg);
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
                message.success('Registration successful, please login');
                registerForm.resetFields();
                setActiveTab('login');
            } else {
                setRegisterError(result?.message || 'Registration failed');
            }
        } catch (error: any) {
            setRegisterError(error?.response?.data?.message || 'Registration failed');
        } finally {
            setRegisterLoading(false);
        }
    };

    return (
        <Modal
            open={open}
            title="Login / Register"
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
                        label: 'Login',
                        children: (
                            <Form form={loginForm} layout="vertical" onFinish={handleLogin}>
                                {loginError && (
                                    <Form.Item>
                                        <Alert message={loginError} type="error" showIcon/>
                                    </Form.Item>
                                )}
                                <Form.Item name="email" rules={[{required: true, message: 'Please enter your email'}]}>
                                    <Input placeholder="Email"/>
                                </Form.Item>
                                <Form.Item name="password"
                                           rules={[{required: true, message: 'Please enter your password'}]}>
                                    <Input.Password placeholder="Password"/>
                                </Form.Item>
                                <Button type="primary" htmlType="submit" loading={loginLoading} block>
                                    Login
                                </Button>
                            </Form>
                        ),
                    },
                    {
                        key: 'register',
                        label: 'Register',
                        children: (
                            <Form form={registerForm} layout="vertical" onFinish={handleRegister}>
                                {registerError && (
                                    <Form.Item>
                                        <Alert message={registerError} type="error" showIcon/>
                                    </Form.Item>
                                )}
                                <Form.Item name="email" rules={[{required: true, message: 'Please enter your email'}]}>
                                    <Input placeholder="Email"/>
                                </Form.Item>
                                <Form.Item name="password"
                                           rules={[{required: true, message: 'Please enter your password'}]}>
                                    <Input.Password placeholder="Password"/>
                                </Form.Item>
                                <Button type="primary" htmlType="submit" loading={registerLoading} block>
                                    Register
                                </Button>
                            </Form>
                        ),
                    },
                ]}
            />
        </Modal>
    );
}
