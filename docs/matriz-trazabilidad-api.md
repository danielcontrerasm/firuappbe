# Matriz de trazabilidad de la API

Esta matriz relaciona las **87 operaciones HTTP distintas de 14 controladores** con el [catálogo de requisitos](catalogo-requisitos.md). Se obtuvo de los mappings del código local revisado el 11 de noviembre de 2026; incluye los cambios locales presentes sobre el commit `830fffe`.

Hay 91 métodos con mappings: crear/editar mascota y crear/editar grupo tienen variantes JSON y multipart para la misma combinación de método y ruta. Se cuentan como cuatro operaciones, no como ocho. `ApiExceptionHandler` es un manejador de errores y no añade endpoints.

«JWT» indica el requisito de autenticación HTTP; **también aplican los permisos por recurso descritos en cada RF**. «Público» permite acceso sin sesión, pero aceptación temporal exige token válido. Grupos PUBLIC siguen requiriendo JWT. Todas las operaciones de transporte y administración requieren ADMIN.

Los enlaces RF llevan a su sección del catálogo, donde se encuentra la fila con el identificador. Esta es trazabilidad de código, no constancia de pruebas ejecutadas ni aprobación de decisiones pendientes.

## AdminMetricsController

Fuente: [AdminMetricsController.java](../src/main/java/com/example/pettracker/controller/AdminMetricsController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/admin/metrics` | [RF-ADM-002](catalogo-requisitos.md#administración-y-panel-web) | ADMIN |

## AlertController

Fuente: [AlertController.java](../src/main/java/com/example/pettracker/controller/AlertController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/alerts` | [RF-ALT-002](catalogo-requisitos.md#geocercas-y-alertas) | JWT |
| DELETE | `/api/alerts/{id}` | [RF-ALT-003](catalogo-requisitos.md#geocercas-y-alertas) | JWT |

## AuthController

Fuente: [AuthController.java](../src/main/java/com/example/pettracker/controller/AuthController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | [RF-AUT-001](catalogo-requisitos.md#autenticación-y-sesión), [RF-PLA-001](catalogo-requisitos.md#planes-y-límites) | Público |
| POST | `/api/auth/login` | [RF-AUT-002](catalogo-requisitos.md#autenticación-y-sesión) | Público |

## GeofenceController

Fuente: [GeofenceController.java](../src/main/java/com/example/pettracker/controller/GeofenceController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/geofences/circle/{petId}` | [RF-GEO-001](catalogo-requisitos.md#geocercas-y-alertas) | JWT |
| POST | `/api/geofences/polygon/{petId}` | [RF-GEO-002](catalogo-requisitos.md#geocercas-y-alertas) | JWT |
| GET | `/api/geofences/{petId}` | [RF-GEO-003](catalogo-requisitos.md#geocercas-y-alertas) | JWT |
| DELETE | `/api/geofences/{petId}` | [RF-GEO-004](catalogo-requisitos.md#geocercas-y-alertas) | JWT |
| DELETE | `/api/geofences/{petId}/{geofenceId}` | [RF-GEO-005](catalogo-requisitos.md#geocercas-y-alertas) | JWT |

## PetAccessController

Fuente: [PetAccessController.java](../src/main/java/com/example/pettracker/controller/PetAccessController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/pets/{petId}/shares` | [RF-ACC-001](catalogo-requisitos.md#acceso-permanente-compartido), [RF-PLA-004](catalogo-requisitos.md#planes-y-límites) | JWT |
| GET | `/api/pets/{petId}/shares` | [RF-ACC-002](catalogo-requisitos.md#acceso-permanente-compartido) | JWT |
| DELETE | `/api/pets/{petId}/shares/{userId}` | [RF-ACC-003](catalogo-requisitos.md#acceso-permanente-compartido) | JWT |

## PetController

Fuente: [PetController.java](../src/main/java/com/example/pettracker/controller/PetController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/pets/locations` | [RF-LOC-001](catalogo-requisitos.md#ubicaciones-e-informes) | JWT |
| GET | `/api/pets` | [RF-MAS-002](catalogo-requisitos.md#mascotas) | JWT |
| GET | `/api/pets/{id}` | [RF-MAS-003](catalogo-requisitos.md#mascotas) | JWT |
| POST | `/api/pets` | [RF-MAS-001](catalogo-requisitos.md#mascotas), [RF-MAS-006](catalogo-requisitos.md#mascotas), [RF-MAS-008](catalogo-requisitos.md#mascotas) | JWT |
| PUT | `/api/pets/{id}` | [RF-MAS-004](catalogo-requisitos.md#mascotas), [RF-MAS-006](catalogo-requisitos.md#mascotas), [RF-MAS-008](catalogo-requisitos.md#mascotas) | JWT |
| GET | `/api/pets/{id}/image` | [RF-MAS-007](catalogo-requisitos.md#mascotas) | JWT |
| DELETE | `/api/pets/{id}` | [RF-MAS-005](catalogo-requisitos.md#mascotas) | JWT |
| POST | `/api/pets/{id}/lost` | [RF-MAS-009](catalogo-requisitos.md#mascotas), [RF-NOT-004](catalogo-requisitos.md#notificaciones) | JWT |
| POST | `/api/pets/{id}/found` | [RF-MAS-010](catalogo-requisitos.md#mascotas), [RF-NOT-004](catalogo-requisitos.md#notificaciones) | JWT |
| GET | `/api/pets/{id}/locations` | [RF-LOC-002](catalogo-requisitos.md#ubicaciones-e-informes), [RF-PLA-003](catalogo-requisitos.md#planes-y-límites), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/pets/{id}/route` | [RF-LOC-003](catalogo-requisitos.md#ubicaciones-e-informes) | JWT |
| GET | `/api/pets/{id}/route/range` | [RF-LOC-004](catalogo-requisitos.md#ubicaciones-e-informes), [RF-PLA-003](catalogo-requisitos.md#planes-y-límites), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/pets/{id}/history/export` | [RF-LOC-007](catalogo-requisitos.md#ubicaciones-e-informes), [RF-LOC-008](catalogo-requisitos.md#ubicaciones-e-informes), [RF-PLA-003](catalogo-requisitos.md#planes-y-límites), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/pets/{id}/neighborhood` | [RF-LOC-005](catalogo-requisitos.md#ubicaciones-e-informes), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/pets/{id}/walk-distance/week` | [RF-LOC-006](catalogo-requisitos.md#ubicaciones-e-informes), [RF-PLA-007](catalogo-requisitos.md#planes-y-límites), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| POST | `/api/pets/{id}/locations` | [RF-LOC-009](catalogo-requisitos.md#ubicaciones-e-informes), [RF-GEO-006](catalogo-requisitos.md#geocercas-y-alertas), [RF-ALT-001](catalogo-requisitos.md#geocercas-y-alertas) | JWT |

## SearchGroupChatController

Fuente: [SearchGroupChatController.java](../src/main/java/com/example/pettracker/controller/SearchGroupChatController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/search-groups/{searchGroupId}/messages` | [RF-CHA-001](catalogo-requisitos.md#chat-de-búsqueda) | JWT |
| POST | `/api/search-groups/{searchGroupId}/messages` | [RF-CHA-002](catalogo-requisitos.md#chat-de-búsqueda) | JWT |

## SearchGroupController

Fuente: [SearchGroupController.java](../src/main/java/com/example/pettracker/controller/SearchGroupController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/search-groups` | [RF-GRU-006](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-008](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-013](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-014](catalogo-requisitos.md#grupos-de-búsqueda), [RF-PLA-005](catalogo-requisitos.md#planes-y-límites) | JWT |
| PUT | `/api/search-groups/{searchGroupId}` | [RF-GRU-007](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-008](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-013](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-014](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/{searchGroupId}` | [RF-GRU-012](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/{searchGroupId}/image` | [RF-GRU-013](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups` | [RF-GRU-009](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/pet/{petId}` | [RF-GRU-011](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| POST | `/api/search-groups/{searchGroupId}/join` | [RF-GRU-001](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-005](catalogo-requisitos.md#grupos-de-búsqueda), [RF-GRU-017](catalogo-requisitos.md#grupos-de-búsqueda), [RF-NOT-006](catalogo-requisitos.md#notificaciones) | JWT |
| POST | `/api/search-groups/{searchGroupId}/volunteers/{userId}` | [RF-GRU-002](catalogo-requisitos.md#grupos-de-búsqueda), [RF-NOT-006](catalogo-requisitos.md#notificaciones) | JWT |
| DELETE | `/api/search-groups/{searchGroupId}/volunteers/{userId}` | [RF-GRU-004](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/{searchGroupId}/member-candidates` | [RF-GRU-016](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| POST | `/api/search-groups/{searchGroupId}/leave` | [RF-GRU-003](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/{searchGroupId}/volunteers` | [RF-GRU-015](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |
| GET | `/api/search-groups/my` | [RF-GRU-010](catalogo-requisitos.md#grupos-de-búsqueda) | JWT |

## TemporaryPetAccessController

Fuente: [TemporaryPetAccessController.java](../src/main/java/com/example/pettracker/controller/TemporaryPetAccessController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/pets/{petId}/temporary-access` | [RF-TMP-001](catalogo-requisitos.md#acceso-temporal-para-cuidadores), [RF-TMP-002](catalogo-requisitos.md#acceso-temporal-para-cuidadores), [RF-TMP-003](catalogo-requisitos.md#acceso-temporal-para-cuidadores), [RF-TMP-008](catalogo-requisitos.md#acceso-temporal-para-cuidadores), [RF-TMP-009](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/pets/{petId}/temporary-access` | [RF-TMP-005](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| DELETE | `/api/pets/{petId}/temporary-access/{accessId}` | [RF-TMP-006](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | JWT |
| GET | `/api/temporary-access/accept` | [RF-TMP-004](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | Público + token |
| POST | `/api/temporary-access/accept` | [RF-TMP-004](catalogo-requisitos.md#acceso-temporal-para-cuidadores) | Público + token |

## UserPlanController

Fuente: [UserPlanController.java](../src/main/java/com/example/pettracker/controller/UserPlanController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/admin/users` | [RF-ADM-001](catalogo-requisitos.md#administración-y-panel-web) | ADMIN |
| PUT | `/api/admin/users/{userId}/plan` | [RF-PLA-002](catalogo-requisitos.md#planes-y-límites) | ADMIN |

## WalkerMarketplaceController

Fuente: [WalkerMarketplaceController.java](../src/main/java/com/example/pettracker/controller/WalkerMarketplaceController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/public/walkers` | [RF-PAS-002](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | Público |
| POST | `/api/walker-applications` | [RF-PAS-001](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | Público |
| GET | `/api/me` | [RF-AUT-004](catalogo-requisitos.md#autenticación-y-sesión) | JWT |
| GET | `/api/walkers/me` | [RF-PAS-003](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | JWT |
| POST | `/api/walk-requests` | [RF-SOL-001](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| GET | `/api/walk-requests` | [RF-SOL-002](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| GET | `/api/walk-requests/{id}` | [RF-SOL-003](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| PUT | `/api/walk-requests/{id}/quote` | [RF-SOL-004](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| PUT | `/api/walk-requests/{id}/decision` | [RF-SOL-005](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| GET | `/api/walk-requests/{id}/messages` | [RF-SOL-006](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| POST | `/api/walk-requests/{id}/messages` | [RF-SOL-007](catalogo-requisitos.md#solicitudes-y-negociación-de-paseos) | JWT |
| POST | `/api/walk-requests/{id}/start` | [RF-PSE-001](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| GET | `/api/dog-walks` | [RF-PSE-002](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| GET | `/api/dog-walks/{id}` | [RF-PSE-003](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| GET | `/api/dog-walks/{id}/positions` | [RF-PSE-005](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| POST | `/api/dog-walks/{id}/positions` | [RF-PSE-004](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| POST | `/api/dog-walks/{id}/complete` | [RF-PSE-006](catalogo-requisitos.md#ejecución-de-paseos) | JWT |
| GET | `/api/admin/walkers` | [RF-PAS-004](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | ADMIN |
| POST | `/api/admin/walkers` | [RF-PAS-005](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | ADMIN |
| PUT | `/api/admin/walkers/{id}` | [RF-PAS-006](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | ADMIN |
| DELETE | `/api/admin/walkers/{id}` | [RF-PAS-007](catalogo-requisitos.md#catálogo-y-administración-de-paseadores) | ADMIN |

## TransitGeofenceController

Fuente: [TransitGeofenceController.java](../src/main/java/com/example/pettracker/transit/controller/TransitGeofenceController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/transit/geofences/vehicle/{vehicleId}` | [RF-TRA-008](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/geofences/vehicle/{vehicleId}` | [RF-TRA-009](catalogo-requisitos.md#transporte) | ADMIN |
| DELETE | `/api/transit/geofences/{geofenceId}` | [RF-TRA-010](catalogo-requisitos.md#transporte) | ADMIN |

## TransitSearchGroupController

Fuente: [TransitSearchGroupController.java](../src/main/java/com/example/pettracker/transit/controller/TransitSearchGroupController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| POST | `/api/transit/search-groups` | [RF-TRA-012](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/search-groups/vehicle/{vehicleId}` | [RF-TRA-013](catalogo-requisitos.md#transporte) | ADMIN |
| POST | `/api/transit/search-groups/{searchGroupId}/join` | [RF-TRA-014](catalogo-requisitos.md#transporte) | ADMIN |
| POST | `/api/transit/search-groups/{searchGroupId}/leave` | [RF-TRA-015](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/search-groups/my` | [RF-TRA-013](catalogo-requisitos.md#transporte) | ADMIN |

## TransitVehicleController

Fuente: [TransitVehicleController.java](../src/main/java/com/example/pettracker/transit/controller/TransitVehicleController.java).

| Método | Ruta | Requisitos | Acceso HTTP |
| --- | --- | --- | --- |
| GET | `/api/transit/vehicles` | [RF-TRA-002](catalogo-requisitos.md#transporte) | ADMIN |
| POST | `/api/transit/vehicles` | [RF-TRA-001](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/vehicles/{id}` | [RF-TRA-003](catalogo-requisitos.md#transporte) | ADMIN |
| POST | `/api/transit/vehicles/{id}/locations` | [RF-TRA-004](catalogo-requisitos.md#transporte), [RF-TRA-011](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/vehicles/{id}/locations/latest` | [RF-TRA-005](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/vehicles/{id}/route` | [RF-TRA-006](catalogo-requisitos.md#transporte) | ADMIN |
| GET | `/api/transit/vehicles/{id}/report` | [RF-TRA-007](catalogo-requisitos.md#transporte) | ADMIN |

## Capacidades sin endpoint propio

La cobertura HTTP se complementa con estos requisitos del catálogo:

| Entrada o proceso | Requisitos principales |
| --- | --- |
| Listener TCP, decodificación, ACK y persistencia GPS | RF-GPS-001 a RF-GPS-010 |
| Evaluación automática de geocercas | RF-GEO-006, RF-GEO-007, RF-ALT-001 |
| Correo, SMS/WhatsApp y broker WebSocket | RF-NOT-001 a RF-NOT-007; RNF-SEG-006, RNF-CON-003 |
| Caducidad y verificación temporal en cada consulta | RF-TMP-007, RF-TMP-008, RF-TMP-009 |
| Datos de demostración al arrancar, login y tareas periódicas | RF-DEM-001 a RF-DEM-003 |
| Navegador, sesión local, mapa, tema y refresco | RF-AUT-005, RF-WEB-001 a RF-WEB-011 |
| Watchdog, métricas, configuración y ciclo del proceso | RNF-OBS-001 a RNF-OBS-005, RNF-OPE-001 a RNF-OPE-006 |
| Funcionalidades futuras y compromisos externos | RF-FUT-001 a RF-FUT-008, RF-COM-001 a RF-COM-003 |

La matriz no atribuye endpoints a funciones previstas, a métodos comentados ni a servicios internos sin ruta expuesta. Tampoco certifica el frontend React externo mencionado en el README.
