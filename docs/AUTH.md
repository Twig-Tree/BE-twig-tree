# 프론트 인증 연동 가이드

이 문서는 Refresh 쿠키 전환 코드 기준입니다. 서버와 프론트를 함께 전환한 뒤 사용합니다.

## 환경과 토큰 보관

| 환경 | 프론트 Origin | API 주소 |
|---|---|---|
| 운영 | `https://app.twig-tree.com` | `https://api.twig-tree.com` |
| 로컬 | `http://localhost:3000` | `http://localhost:8080` |

- Access Token은 응답 JSON에서 받아 브라우저 메모리에만 보관합니다. 일반 API에는 `Authorization: Bearer <accessToken>`을 보냅니다.
- Refresh Token은 `refresh_token` HttpOnly 쿠키로 발급합니다. 프론트가 읽거나 요청 본문에 넣지 않습니다.
- 인증 API 호출에는 `credentials: 'include'`(Axios는 `withCredentials: true`)를 사용합니다.
- 운영 CORS와 인증 Origin 허용 목록에는 앱 및 API Origin만 포함됩니다. 로컬 프론트는 로컬 백엔드에 연결합니다.

Refresh 쿠키는 `Path=/auth`, `SameSite=Lax`, Domain 생략으로 발급되며 수명은 14일입니다. 운영에서는 `Secure`를 적용합니다. CSRF 토큰은 발급하거나 요구하지 않습니다. 대신 서버는 로그인·재발급·로그아웃 POST의 `Origin`이 설정된 허용 목록과 정확히 일치해야 처리합니다. `Origin`이 없거나 `null`이거나 다른 값이면 403으로 거부합니다. 브라우저는 POST의 `Origin`을 자동으로 전송하므로 프론트 코드가 직접 설정하지 않습니다. CLI 호출은 허용된 `Origin` 헤더를 명시해야 합니다. CORS와 `SameSite`만으로 이 검증을 대체하지 않습니다.

## API 계약

| 요청 | 요청 본문 | 필요한 인증 정보 | 성공 응답 data |
|---|---|---|---|
| `POST /auth/google` | `{ "idToken": "구글 ID 토큰" }` | 허용된 Origin | `accessToken`, `member` |
| `POST /auth/refresh` | 없음 | 허용된 Origin + Refresh 쿠키 | `accessToken`, `member` |
| `POST /auth/logout` | 없음 | 허용된 Origin, Refresh 쿠키는 있으면 전송 | `null` |

로그인·재발급 성공 시 Refresh 쿠키를 발급·갱신합니다. 응답 본문에 `refreshToken`은 없습니다. 세 API는 Access Token이 없어도 호출할 수 있습니다. 인증 응답에는 `Cache-Control: no-store`를 설정합니다. `GET /auth/csrf`는 제거했습니다.

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

## 브라우저 호출 예시

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

async function login(idToken) {
  return readResponse(await fetch(`${API}/auth/google`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken }),
  }));
}

async function refresh() {
  return readResponse(await fetch(`${API}/auth/refresh`, {
    method: 'POST', credentials: 'include',
  }));
}

async function logout() {
  return readResponse(await fetch(`${API}/auth/logout`, {
    method: 'POST', credentials: 'include',
  }));
}
```

## 인증 상태와 오류 처리

1. 앱 최초 로드·새로고침 시 `/auth/refresh`로 Access Token과 회원 정보를 복원합니다. 복원이 끝날 때까지 인증 상태를 '확인 중'으로 둡니다.
2. 일반 API의 Access Token 만료(`AUTH401-2`) 시 재발급을 한 번 수행하고 원래 요청을 한 번 재시도합니다. 여러 요청의 재발급은 하나의 진행 중 Promise를 공유합니다.
3. 재발급 API 자체는 일반 API의 자동 재발급 인터셉터에서 제외합니다. 여러 탭은 쿠키를 공유하므로 필요하면 탭 간 재발급도 조정합니다.
4. 로그아웃 시 메모리의 Access Token·회원 상태를 비우고 새 재발급을 시작하지 않습니다. 이미 진행 중인 재발급이 완료된 뒤 로그아웃 요청을 보내 최신 Refresh 쿠키를 폐기합니다.

| 상태·코드 | 프론트 처리 |
|---|---|
| 401 `AUTH401-1` | 구글 로그인 실패 처리 |
| 401 `AUTH401-2` | Access 만료: 재발급 후 원래 요청을 최대 한 번 재시도 |
| 401 `AUTH401-3` | 유효하지 않은 Access Token: 무한 재시도 없이 인증 상태 복구 또는 재로그인 |
| 401 `AUTH401-4`, `AUTH401-5` | Refresh 부재·무효·만료: 메모리 인증 상태를 비우고 재로그인 |
| 403 `COMMON403-1` | 인증 POST라면 허용된 Origin에서 호출했는지 확인 |
| 503 `AUTH503-1` | 일시적 토큰 저장소 장애: 완료로 처리하지 말고 재시도 안내 |

CORS 차단 응답은 브라우저가 본문 읽기를 막을 수 있어 프론트에는 네트워크 오류처럼 보일 수 있습니다. 로그아웃은 쿠키가 없거나 Refresh Token이 만료되어도 성공하고 삭제 쿠키를 보냅니다. Redis 장애 시에는 503을 반환하고 Refresh 쿠키를 유지합니다. 이미 발급된 Access Token은 만료(현재 최대 30분)까지 유효합니다.

## 연동 확인

Swagger는 API Origin에서 열어 인증 POST를 호출합니다. 브라우저가 `Origin`을 자동 전송하며 Refresh 쿠키도 자동으로 전송합니다. 일반 업무 API의 Bearer 헤더는 별도로 설정해야 합니다.

- 로그인 본문에 Refresh Token이 없고 Refresh 쿠키가 저장되는지 확인합니다.
- 새로고침 후 회원 정보가 복원되는지 확인합니다.
- Access 만료 시 중복 재발급과 무한 재시도 없이 요청이 복구되는지 확인합니다.
- 로그아웃 후 Refresh 쿠키가 삭제되고 메모리 인증 상태가 복구되지 않는지 확인합니다.
- 인증 POST에서 허용된 Origin은 성공하고, Origin 누락·불일치·`null`은 403인지 확인합니다.
- 실제 운영 브라우저에서 CORS, 쿠키 속성 및 Google 로그인까지 검증합니다.
