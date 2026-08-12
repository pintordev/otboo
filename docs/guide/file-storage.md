# 파일 저장소 (FileStorageService) 사용 가이드

## 1. application.yaml 설정

otboo.file 프리픽스로 설정합니다. local (로컬 디스크) 또는 s3 (S3) 중 impl 값으로 선택합니다.

### 로컬 모드 (기본값, 별도 설정 없어도 local로 동작)

```
otboo:
  file:
    impl: local
    public-base-url: http://localhost:8080/uploads
    max-size-bytes: 5242880
    allowed-extensions: jpg,jpeg,png,webp
    local:
      upload-dir: ./uploads

spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```

### S3 모드

```
otboo:
  file:
    impl: s3
    public-base-url: https://cdn.otboo.us
    max-size-bytes: 5242880
    allowed-extensions: jpg,jpeg,png,webp
    s3:
      bucket: otboo-image
      region: ap-northeast-2
```

S3 모드에서는 AWS 자격증명이 별도로 필요합니다. application.yaml에 Access Key나 Secret을 직접 넣지 마세요. 환경변수
AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY, 또는 AWS_PROFILE로 로컬에 설정하면 AWS SDK가 자동으로 인식합니다.

impl, public-base-url, max-size-bytes, allowed-extensions 4개는 필수 검증 대상입니다. 하나라도 빠지면 앱 자체가 기동에 실패합니다.
새 프로파일을 추가한다면 이 4개는 꼭 같이 넣어주세요. spring.servlet.multipart.max-file-size도 otboo.file.max-size-bytes와
반드시 맞춰야 합니다.

---

## 2. FileStorageService, FileUrlResolver

```
public interface FileStorageService {
  String store(MultipartFile file, String domain);
  void delete(String key);
}
```

- store는 완성된 URL이 아니라 상대 경로 형태의 키를 반환합니다. 예를 들면 clothes/3f2a-uuid.jpg 같은 형태입니다. DB에는 이 키만 저장합니다.
- **저장할 때 domain에 본인이 개발 중이거나 저장할 폴더명을 작성하면 됩니다.**

```
public class FileUrlResolver {
  public String resolve(String key);
}
```

resolve는 저장된 키를 완성된 URL로 바꿔줍니다. 응답 DTO로 내보내기 직전에만 호출하면 됩니다.

원칙은 두 가지입니다. 저장할 때는 store가 반환한 키를 그대로 저장한다. 응답으로 내보낼 때는 반드시 resolve를 한 번 거친다.

---

## 3. 반영 후 체크리스트

세 파일을 다 고쳤으면 다음을 확인하세요.

- 이미지 없이 옷 등록/수정하는 케이스가 여전히 잘 되는지 (store, delete가 호출되면 안 되는 경로)
- 새 이미지로 등록했을 때 클로스 목록 조회 응답의 imageUrl이 완성된 URL로 나오는지
- 추천 화면 (추천 API 응답)의 ootd 목록에도 imageUrl이 완성된 URL로 나오는지
- 기존 이미지가 있는 옷을 새 이미지로 수정했을 때 예전 이미지가 삭제되는지 (local 모드면 uploads 폴더에서 파일이 실제로 사라지는지 확인 가능)
- 피드/팔로우/DM/댓글 응답에 나오는 작성자 프로필 이미지가 완성된 URL로 나오는지 (UserSummaryQueryRepositoryImpl 반영 여부 확인)

---

## 4. 로컬 모드에서 이미지가 안 보인다면

로컬 모드는 uploads 경로로 파일을 서빙합니다. SecurityConfig에도 이미 permitAll 처리돼 있어서 인증 없이 접근 가능합니다. 별도 설정 없이
otboo.file.impl을 local로 두거나 생략하면 바로 됩니다.

S3 모드는 버킷이 기본적으로 비공개라, 지금 당장은 업로드와 삭제 동작 확인까지만 되고 브라우저에서 직접 이미지가 보이지는 않습니다. CDN 붙는 건 별도로 진행 중이니, 그
전까지는 로컬 모드로 개발과 테스트를 하시는 걸 추천드립니다.
