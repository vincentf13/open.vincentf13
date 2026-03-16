import http from 'k6/http';
import {sleep} from 'k6';

export const options = {
    vus: 100, // 固定並發數
    duration: '10000h', // 執行到手動中斷為止
};
export default function () {
    // 移除 sleep(1) 以產生最大壓力
}
