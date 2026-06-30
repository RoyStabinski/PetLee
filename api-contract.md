
# Pet-Lee REST Contract (FROZEN — do not change without telling the other engineer)

Base path: `/api`
JSON only. All bodies are UTF-8 JSON unless marked multipart.
Auth = a valid server session (created at login). Endpoints marked "auth" reject with 401 if not logged in.

---

## USER / AUTH  (Engineer A)

### POST /api/users/register   — open
Request:
{
"username": "donaldt",
"password": "12345678",
"fullName": "Donald Trump",
"email": "djt@usa.com",
"phone": "050-1234567",
"region": "Washington DC"
}
Response 200 (UserDTO — never includes password):
{
"id": 1,
"username": "donaldt",
"fullName": "Donald Trump",
"email": "djt@usa.com",
"phone": "050-1234567",
"role": "USER"
}
Errors: 409 if username/email already exists.

### POST /api/auth/login   — open
Request:
{ "username": "donaldt", "password": "12345678" }
Response 200: same UserDTO as above, plus a session is created.
Errors: 401 if credentials are wrong.

### POST /api/auth/logout   — auth
Request: (empty)
Response 204: no body. Session invalidated.

---

## CATEGORIES  (Engineer B)

### GET /api/categories   — open
Response 200 (List<CategoryDTO>):
[
{ "id": 1, "name": "Dogs" },
{ "id": 2, "name": "Cats" },
{ "id": 3, "name": "Rodents" }
]

---

## PETS  (Engineer B)

### GET /api/pets   — open
Optional filter params: ?categoryId=1&size=SMALL&gender=MALE
Response 200 (List<PetDTO> — gallery view, main image only, newest first):
[
{
"id": 10,
"name": "Rex",
"shortDesc": "Friendly and energetic",
"age": 3,
"size": "MEDIUM",
"gender": "MALE",
"status": "AVAILABLE",
"categoryName": "Dogs",
"mainImageUrl": "/images/rex-main.jpg"
}
]

### GET /api/pets/{id}   — open*
Response 200 (PetDetailDTO — full info + all images).
*Owner contact fields are filled ONLY if the caller is logged in; otherwise null.
{
"id": 10,
"name": "Rex",
"breed": "Jack Russell",
"age": 3,
"size": "MEDIUM",
"gender": "MALE",
"shortDesc": "Friendly and energetic",
"longDesc": "Full description here...",
"status": "AVAILABLE",
"categoryName": "Dogs",
"images": [
{ "id": 100, "imageUrl": "/images/rex-main.jpg", "isMain": true },
{ "id": 101, "imageUrl": "/images/rex-2.jpg", "isMain": false }
],
"ownerFullName": "Roy Stein",
"ownerEmail": "roy@example.com",
"ownerPhone": "050-1234567"
}

### POST /api/pets   — auth
Request (PetForm):
{
"name": "Rex",
"breed": "Jack Russell",
"age": 3,
"size": "MEDIUM",
"gender": "MALE",
"shortDesc": "Friendly and energetic",
"longDesc": "Full description here...",
"categoryId": 1
}
Response 200: the created PetDTO.

### PUT /api/pets/{id}   — auth + owner
Request: same PetForm as POST.
Response 200: updated PetDTO.
Errors: 403 if not the owner. 409 if a concurrent edit happened (optimistic lock).

### DELETE /api/pets/{id}   — owner or admin
Response 204: no body. All images cascade-deleted.
Errors: 403 if not owner and not admin.

### POST /api/pets/{id}/images   — auth + owner
Request: multipart/form-data — file + boolean "isMain".
Response 200 (PetImageDTO):
{ "id": 102, "imageUrl": "/images/rex-3.jpg", "isMain": false }

---

## ENUM VALUES (shared — use these exact strings)
size:   SMALL | MEDIUM | LARGE
gender: MALE | FEMALE
status: AVAILABLE | ADOPTED | REMOVED
role:   USER | ADMIN

