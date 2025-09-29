import http from 'k6/http';
import { sleep } from 'k6';
export const options = { stages: [{duration:'5m',target:200}] };
export default function () {
  http.get('http://127.0.0.1:8081/burn?ms=200');
  sleep(1);
}