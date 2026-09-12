
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
"imageUrl": "/images/rex-main.jpg"
}
]

### GET /api/pets/mine   — auth   (extension — ADR-002 #13)
Every listing the caller owns, in every status (not just AVAILABLE). Same shape as GET /api/pets.
Response 200 (List<PetDTO>). Errors: 401 if not logged in.

### GET /api/pets/{id}   — open*
Response 200 (PetDetailDTO — full info, one photograph).
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
"imageUrl": "/images/rex-main.jpg",
"ownerName": "Roy Stein",
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
Response 204: no body. Its photograph is deleted with it.
Errors: 403 if not owner and not admin.

Photo upload is **not** available over `/api`. See ADR-002 #10: Jersey cannot inject a Servlet
`Part` as a `@FormParam`, and the two ways to fix that are both closed by ADR-003. Uploading a
photo is a JSF-only action — `addPet.xhtml`'s `<h:inputFile>` posts straight to the Faces servlet,
never to this API.

---

## ADMIN  (extension — see ADR-002 rows 6 and 12; not part of the frozen set)

Every endpoint here requires a session whose role is ADMIN: 401 with no session, 403 code
NOT_ADMIN for a USER. Deleting a listing is not here — it is DELETE /api/pets/{id}, which already
allows the owner or an admin.

### GET /api/admin/pets   — admin
Optional filter params: ?categoryId=1&size=SMALL&gender=MALE  (same as GET /api/pets)
Response 200 (List<PetDTO> — every status, newest first; see ADR-002 #12 — no owner name or
creation date, since nothing reads this endpoint from outside the admin service layer):
[
{
"id": 10,
"name": "Rex",
"shortDesc": "Friendly and energetic",
"age": 3,
"size": "MEDIUM",
"gender": "MALE",
"status": "REMOVED",
"categoryName": "Dogs",
"imageUrl": "/images/rex-main.jpg"
}
]

### PUT /api/admin/pets/{id}/status   — admin   (ADR-002 #11: query param, not a JSON body)
Request: ?status=REMOVED
Response 200: the updated PetDTO.
Errors: 400 if status is anything but REMOVED or AVAILABLE. 404 if no such pet.

### POST /api/categories   — admin
Request:
{ "name": "Birds" }
Response 200 (CategoryDTO):
{ "id": 7, "name": "Birds" }
Errors: 400 if the name is blank or over 50 characters. 409 code CATEGORY_EXISTS if the name is
already taken (case-insensitively).

### DELETE /api/categories/{id}   — admin
Response 204: no body.
Errors: 404 if no such category. 409 code CATEGORY_IN_USE if any listing still references it,
REMOVED listings included.

---

## ENUM VALUES (shared — use these exact strings)
size:   SMALL | MEDIUM | LARGE
gender: MALE | FEMALE
status: AVAILABLE | ADOPTED | REMOVED
role:   USER | ADMIN

