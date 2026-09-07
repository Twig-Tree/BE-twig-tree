# 프론트 인증 연동 가이드

이 문서는 Refresh 쿠키·CSRF 전환 코드 기준입니다. 서버와 프론트를 함께 전환한 뒤 사용합니다. HTTPS만 배포된 서버는 아직 이전 인증 계약을 사용합니다.

## 1. 환경과 토큰 보관

| 환경 | 프론트 Origin | API 주소 |
|---|---|---|
| 운영 | `https://app.twig-tree.com` | `https://api.twig-tree.com` |
| 로컬 | `http://localhost:3000` | `http://localhost:8080` |

- Access Token은 응답 JSON에서 받아 브라우저 메모리에만 보관합니다. localStorage·sessionStorage 및 상태 라이브러리의 영속 저장 기능에 넣지 않습니다.
- SSR 서버의 전역 변수에는 사용자 토큰을 저장하지 않습니다. 아래 흐름은 브라우저 실행 기준입니다.
- Refresh Token은 서버가 HttpOnly 쿠키로 발급합니다. 프론트가 읽거나 본문에 담아 보내지 않습니다.
- 모든 인증 API 호출에는 `credentials: 'include'`(Axios는 `withCredentials: true`)를 사용합니다.
- 일반 API에는 `Authorization: Bearer <accessToken>`을 보냅니다. Access Token은 쿠키로 인증하지 않습니다.
- 운영 CORS에는 로컬·Vercel 미리보기 주소가 포함되지 않습니다. 로컬 프론트는 로컬 백엔드에 연결합니다.

| 쿠키 | Path | 수명 | 공통 속성 |
|---|---|---|---|
| `refresh_token` | `/auth` | 현재 14일, 재발급 시 갱신 | HttpOnly, SameSite=Lax, Domain 생략 |
| 운영 `__Host-csrf_token` / 로컬 `csrf_token` | `/` | 세션 쿠키 | HttpOnly, SameSite=Lax, Domain 생략 |

운영 쿠키는 Secure를 적용하고 로컬 HTTP에서는 해제합니다. CSRF 쿠키의 운영 이름은 다른 서브도메인의 Domain 쿠키 주입을 막기 위해 `__Host-` 접두사를 사용합니다. 프론트는 이 쿠키 이름이나 값을 직접 다룰 필요가 없습니다.

## 2. API 계약

| 요청 | 요청 본문 | 필요한 인증 정보 | 성공 응답 data |
|---|---|---|---|
| `GET /auth/csrf` | 없음 | 없음 | `token`, `headerName` |
| `POST /auth/google` | `{ "idToken": "구글 ID 토큰" }` | CSRF 쿠키 + `X-XSRF-TOKEN` | `accessToken`, `member` |
| `POST /auth/refresh` | 없음 | Refresh 쿠키 + CSRF 쿠키 + `X-XSRF-TOKEN` | `accessToken`, `member` |
| `POST /auth/logout` | 없음 | CSRF 쿠키 + `X-XSRF-TOKEN`, Refresh 쿠키는 있으면 전송 | `null` |

로그인·재발급 성공 시 Refresh 쿠키를 발급·갱신합니다. 응답 본문에 `refreshToken`은 없습니다. 위 네 API는 Access Token이 없어도 호출할 수 있지만, 인증 POST 요청의 CSRF 검증은 필수입니다. 인증 응답과 CSRF 발급 응답은 `Cache-Control: no-store`입니다.

CSRF 발급 응답 예시:

```json
{
  "isSuccess": true,
  "code": "AUTH200-4",
  "message": "CSRF 토큰을 발급했습니다.",
  "data": { "token": "서버가 발급한 값", "headerName": "X-XSRF-TOKEN" }
}
```

프론트는 `data.token`을 그대로 메모리에 보관해 헤더로 보냅니다. 응답 토큰은 마스킹되어 있어 쿠키 값과 다를 수 있습니다. 직접 디코딩하거나 쿠키 값으로 대체하지 않습니다.

로그인·재발급의 data 형식:

```json
{
  "accessToken": "자체 액세스 JWT",
  "member": {
    "memberId": 1,
    "email": "user@example.com",
    "name": "사용자",
    "profileImage": null
  }
}
```

성공 코드는 로그인 `AUTH200-1`, 재발급 `AUTH200-2`, 로그아웃 `AUTH200-3`입니다.

## 3. 브라우저 호출 예시

아래 예시는 CSRF 발급과 로그인 요청의 최소 형태입니다. 반환된 토큰은 앱의 브라우저 메모리 상태에 저장합니다. 운영에서는 API 주소를 `https://api.twig-tree.com`으로 설정합니다.

```javascript
const API = 'http://localhost:8080';

async function readResponse(response) {
  const body = await response.json();
  if (!response.ok) {
    throw Object.assign(new Error(body.message), {
      status: response.status, code: body.code,
    });
  }
  return body.data;
}

async function fetchCsrf() {
  return readResponse(await fetch(`${API}/auth/csrf`, {
    credentials: 'include', cache: 'no-store',
  }));
}

async function login(idToken) {
  const csrf = await fetchCsrf();
  const data = await readResponse(await fetch(`${API}/auth/google`, {
    method: 'POST', credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ idToken }),
  }));
  return { ...data, csrf };
}

async function refresh(csrf) {
  return readResponse(await fetch(`${API}/auth/refresh`, {
    method: 'POST', credentials: 'include',
    headers: { [csrf.headerName]: csrf.token },
  }));
}

async function logout(csrf) {
  return readResponse(await fetch(`${API}/auth/logout`, {
    method: 'POST', credentials: 'include',
    headers: { [csrf.headerName]: csrf.token },
  }));
}
```

이 예시는 네트워크 오류·재시도·동시성 제어를 포함한 완성된 인증 클라이언트가 아닙니다. 다음 규칙을 앱에 연결합니다.

## 4. 인증 상태와 오류 처리

1. 앱 최초 로드·새로고침 시 `/auth/csrf`를 호출하고 `/auth/refresh`로 Access Token과 회원 정보를 복원합니다. 복원이 끝날 때까지 인증 상태를 '확인 중'으로 둡니다.
2. 일반 API의 Access Token 만료(`AUTH401-2`) 시 재발급을 한 번 수행하고 원래 요청을 한 번 재시도합니다. 여러 요청의 재발급은 하나의 진행 중 Promise를 공유합니다.
3. 재발급 API 자체는 일반 API의 자동 재발급 인터셉터에서 제외합니다. 여러 탭은 쿠키를 공유하므로, 필요하면 BroadcastChannel 또는 Web Locks 등으로 탭 간 재발급도 조정합니다.
4. 로그아웃 시 메모리의 Access Token·회원 상태를 비우고 새 재발급을 시작하지 않습니다. 이미 진행 중인 재발급이 완료된 뒤 로그아웃 요청을 보내 최신 Refresh 쿠키를 폐기합니다. 늦게 도착한 응답이 메모리 인증 상태를 복구하지 않도록 무시합니다.

| 상태·코드 | 프론트 처리 |
|---|---|
| 401 `AUTH401-1` | 구글 로그인 실패 처리 |
| 401 `AUTH401-2` | Access 만료: 재발급 후 원래 요청을 최대 한 번 재시도 |
| 401 `AUTH401-3` | 유효하지 않은 Access Token: 무한 재시도 없이 인증 상태 복구 또는 재로그인 |
| 401 `AUTH401-4`, `AUTH401-5` | Refresh 부재·무효·만료: 메모리 인증 상태를 비우고 로그인 필요 상태로 전환 |
| 403 `AUTH403-1` | CSRF를 다시 발급받고 실패한 인증 요청을 최대 한 번 재시도 |
| 403 그 외 | 권한 또는 CORS 문제 확인. 토큰 재발급 루프에 넣지 않음 |
| 503 `AUTH503-1` | 일시적 토큰 저장소 장애: 완료로 처리하지 말고 재시도 안내 |
| 네트워크 오류 | 서버 처리 여부가 불확실하므로 로그인·재발급·로그아웃을 무제한 자동 반복하지 않음 |

CSRF 실패는 컨트롤러 실행 전에 차단되므로 해당 오류 코드일 때만 CSRF 복구 후 재시도합니다. CORS 차단 응답은 브라우저가 본문 읽기를 막을 수 있어 프론트에는 네트워크 오류처럼 보일 수 있습니다.

로그아웃은 쿠키가 없거나 Refresh Token이 만료되어도 성공하고 삭제 쿠키를 보냅니다. Redis 장애 시에는 503을 반환하고 Refresh 쿠키를 유지하므로, 화면의 로컬 로그아웃과 서버 토큰 폐기 완료를 구분해 재시도할 수 있게 합니다. 이미 복사된 Access Token은 기존 정책상 만료(현재 최대 30분)까지 유효합니다.

## 5. Swagger 및 연동 완료 확인

Swagger에서는 먼저 `GET /auth/csrf`를 호출하고 응답의 `data.token`을 인증 POST의 `X-XSRF-TOKEN` 입력란에 넣습니다. 같은 API Origin에서 열린 Swagger는 쿠키를 자동 전송합니다. 일반 업무 API의 Bearer 헤더는 별도로 설정해야 합니다.

- 로그인 본문에 Refresh Token이 없고 Refresh 쿠키가 저장되는지 확인합니다.
- 새로고침 후 회원 정보가 복원되는지 확인합니다.
- Access 만료 시 중복 재발급과 무한 재시도 없이 요청이 복구되는지 확인합니다.
- Refresh 만료 시 재로그인으로 전환하는지 확인합니다.
- 로그아웃 후 Refresh 쿠키가 삭제되고 메모리 인증 상태가 복구되지 않는지 확인합니다.
- CSRF 누락·불일치는 403, 정상 CSRF와 쿠키 조합은 성공하는지 확인합니다.
- 실제 운영 브라우저에서 CORS, 쿠키 속성 및 Google 로그인까지 검증합니다. 로컬 MockMvc 테스트만으로 운영 연동 완료를 판단하지 않습니다.
