---

## 1단계. GitHub 릴리즈 + APK 업로드

1. 이 저장소(CU-)에서 오른쪽 **Releases** 클릭
2. **Draft a new release** 클릭
3. 아래처럼 입력:
   - Tag: `v1.2.0` ← 버전에 맞게 (Choose a tag 칸에 직접 입력 후 Create new tag 클릭)
   - Title: `v1.2.0`
   - Description: 업데이트 내용 자유롭게 작성
4. APK 파일을 아래 첨부 칸에 끌어다 놓기 (파일명을 `1.2.0.apk` 처럼 버전명으로 바꿔서 올리기)
5. **Publish release** 클릭

---

## 2단계. version.json 수정

1. 이 저장소 메인 화면에서 `version.json` 파일 클릭
2. 오른쪽 위 **연필 아이콘(Edit)** 클릭
3. 아래 내용으로 수정:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/bugea0002/CU-/releases/download/v1.2.0/1.2.0.apk",
  "releaseNotes": "여기에 업데이트 내용 작성"
}

▎ 주의: versionCode는 AI가 설정한 숫자와 반드시 같아야 합니다.
▎ apkUrl의 버전 번호도 실제 업로드한 파일명과 일치해야 합니다.

4. Commit changes 클릭

---
배포 확인

version.json 저장 후, 기존 앱에서 설정 → 업데이트 확인을 눌러
새 버전이 감지되는지 확인합니다.
