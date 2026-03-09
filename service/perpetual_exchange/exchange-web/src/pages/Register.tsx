import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Button, Card, Form, Input, message} from 'antd';
import {LockOutlined, UserOutlined} from '@ant-design/icons';
import {register} from '../api/auth';

export default function Register() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);

    const handleRegister = async (values: {
        email: string;
        password: string;
        confirm: string;
    }) => {
        if (values.password !== values.confirm) {
            message.error('Passwords do not match');
            return;
        }

        setLoading(true);
        try {
            const result = await register(values.email, values.password);

            if (result.code === '0') {
                message.success('Registration successful! Please login');
                navigate('/login');
            } else {
                message.error(result.message || 'Registration failed');
            }
        } catch (error: any) {
            message.error(error.response?.data?.message || 'Registration failed');
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
            <Card title="Register Account" style={{width: 400}}>
                <Form onFinish={handleRegister}>
                    <Form.Item
                        name="email"
                        rules={[
                            {required: true, message: 'Please enter your email'},
                            {type: 'email', message: 'Please enter a valid email'}
                        ]}
                    >
                        <Input
                            prefix={<UserOutlined/>}
                            placeholder="Email"
                        />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[
                            {required: true, message: 'Please enter your password'},
                            {min: 8, message: 'Password must be at least 8 characters'}
                        ]}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            placeholder="Password"
                        />
                    </Form.Item>

                    <Form.Item
                        name="confirm"
                        rules={[{required: true, message: 'Please confirm your password'}]}
                    >
                        <Input.Password
                            prefix={<LockOutlined/>}
                            placeholder="Confirm Password"
                        />
                    </Form.Item>

                    <Form.Item>
                        <Button
                            type="primary"
                            htmlType="submit"
                            loading={loading}
                            block
                        >
                            Register
                        </Button>
                    </Form.Item>

                    <Button type="link" onClick={() => navigate('/login')} block>
                        Already have an account? Login now
                    </Button>
                </Form>
            </Card>
        </div>
    );
}
