import http from 'k6/http';
import { sleep } from 'k6';
export const options = { stages: [{duration:'30s',target:50},{duration:'2m',target:200},{duration:'30s',target:0}] };
export default function () {
  http.get('http://127.0.0.1/service-template/burn?ms=200');
  sleep(1);
}
