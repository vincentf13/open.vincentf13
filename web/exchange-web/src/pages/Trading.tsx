import { useState } from 'react';
import { Card, InputNumber, Button, Radio, Space, message, Statistic, Row, Col } from 'antd';

export default function Trading() {
  const [orderType, setOrderType] = useState<'LIMIT' | 'MARKET'>('LIMIT');
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY');
  const [price, setPrice] = useState<number>(0);
  const [quantity, setQuantity] = useState<number>(0);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async () => {
    if (orderType === 'LIMIT' && !price) {
      message.error('請輸入價格');
      return;
    }
    if (!quantity) {
      message.error('請輸入數量');
      return;
    }

    setLoading(true);
    try {
      // TODO: 調用下單 API
      // await submitOrder({ type: orderType, side, price, quantity });

      message.success('下單成功！');
    } catch (error) {
      message.error('下單失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 24, minHeight: '100vh', background: '#f0f2f5' }}>
      <Row gutter={[16, 16]}>
        {/* 行情顯示 */}
        <Col span={24}>
          <Card title="BTC/USDT 行情">
            <Row gutter={16}>
              <Col span={6}>
                <Statistic title="最新價" value={50000} suffix="USDT" />
              </Col>
              <Col span={6}>
                <Statistic
                  title="漲跌幅"
                  value={5.28}
                  suffix="%"
                  valueStyle={{ color: '#cf1322' }}
                />
              </Col>
              <Col span={6}>
                <Statistic title="24h 最高" value={51000} suffix="USDT" />
              </Col>
              <Col span={6}>
                <Statistic title="24h 最低" value={49000} suffix="USDT" />
              </Col>
            </Row>
          </Card>
        </Col>

        {/* 下單表單 */}
        <Col span={12}>
          <Card title="下單">
            <Space direction="vertical" style={{ width: '100%' }} size="large">
              {/* 訂單類型 */}
              <div>
                <div style={{ marginBottom: 8 }}>訂單類型</div>
                <Radio.Group
                  value={orderType}
                  onChange={e => setOrderType(e.target.value)}
                >
                  <Radio.Button value="LIMIT">限價</Radio.Button>
                  <Radio.Button value="MARKET">市價</Radio.Button>
                </Radio.Group>
              </div>

              {/* 買賣方向 */}
              <div>
                <div style={{ marginBottom: 8 }}>買賣方向</div>
                <Radio.Group
                  value={side}
                  onChange={e => setSide(e.target.value)}
                >
                  <Radio.Button value="BUY">買入</Radio.Button>
                  <Radio.Button value="SELL">賣出</Radio.Button>
                </Radio.Group>
              </div>

              {/* 價格（限價單才顯示） */}
              {orderType === 'LIMIT' && (
                <div>
                  <div style={{ marginBottom: 8 }}>價格</div>
                  <InputNumber
                    style={{ width: '100%' }}
                    placeholder="輸入價格"
                    value={price}
                    onChange={val => setPrice(val || 0)}
                    min={0}
                  />
                </div>
              )}

              {/* 數量 */}
              <div>
                <div style={{ marginBottom: 8 }}>數量</div>
                <InputNumber
                  style={{ width: '100%' }}
                  placeholder="輸入數量"
                  value={quantity}
                  onChange={val => setQuantity(val || 0)}
                  min={0}
                />
              </div>

              {/* 提交按鈕 */}
              <Button
                type="primary"
                size="large"
                block
                loading={loading}
                onClick={handleSubmit}
                danger={side === 'SELL'}
              >
                {side === 'BUY' ? '買入' : '賣出'} BTC
              </Button>
            </Space>
          </Card>
        </Col>

        {/* 訂單簿（佔位） */}
        <Col span={12}>
          <Card title="訂單簿">
            <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>
              訂單簿數據（待實現）
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
