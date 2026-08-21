import http from 'k6/http';

export const options = {
  scenarios: {
    blast: {
      executor: 'per-vu-iterations',
      vus: 200,          // 200 real concurrent users hitting at once
      iterations: 1,     // 1 request per user
    },
  },
};

export default function () {
  // Randomly splits the 200 users evenly between your two ports
  const port = Math.random() < 0.5 ? '8081' : '8082';
  http.get(`http://localhost:${port}/api/users/1`);
}
