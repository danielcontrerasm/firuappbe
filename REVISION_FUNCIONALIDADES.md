# Revisión de funcionalidades

Objetivo: revisar cada funcionalidad para decidir qué código es necesario para que la aplicación arranque y qué puede retirarse. No se considera revisada una funcionalidad hasta documentar su decisión y comprobar el recorrido que se conserva.

## Criterios por revisión

- [ ] Identificar controladores, servicios, entidades, repositorios, configuración y pantallas relacionados.
- [ ] Trazar dependencias con otras funcionalidades y con el arranque de Spring.
- [ ] Clasificar cada parte como **necesaria**, **opcional** o **retirable**.
- [ ] Documentar qué APIs, tablas, variables de entorno e integraciones afecta.
- [ ] Aplicar cambios pequeños y reversibles; actualizar documentación y configuración.
- [ ] Verificar el arranque y el recorrido que se decidió conservar.

## Orden de revisión

### Base mínima de la aplicación

- [ ] Arranque Spring Boot, configuración y manejo de errores
- [ ] Conexión a PostgreSQL/PostGIS, inicialización de esquema y validación JPA
- [ ] Seguridad compartida: JWT, roles, CORS y usuario actual
- [ ] Dashboard web estático y carga de recursos
- [ ] Configuración de despliegue y variables de entorno

### Funcionalidades del producto

Los requisitos de cada etapa están en el [catálogo de requisitos funcionales](docs/catalogo-requisitos.md#requisitos-funcionales).

#### Cuenta y sesión

- [ ] Registro, confirmación de correo, inicio de sesión y sesión JWT
- [ ] Carga del perfil del usuario
- [ ] Carga del menú y sus opciones según usuario/rol

#### Planes y límites

- [ ] Asignación de plan y límites de uso

#### Mascotas y rastreadores

- [ ] Carga de la lista de mascotas
- [ ] Carga del perfil de cada mascota

#### Acceso compartido y temporal

- [ ] Acceso temporal y compartición de mascotas

#### Dispositivos GPS e ingestión

- [x] Ingesta y carga de datos GPS — revisión estática; hallazgos documentados; carga y validación con BD pendientes
- [ ] Manejo de ejecuciones concurrentes de GPS

#### Ubicaciones e informes

- [ ] Creación y persistencia de ubicaciones
- [ ] Carga de rutas para ventanas de 10 minutos y 3 horas
- [ ] Carga de la ruta del día
- [ ] Cálculo y carga de recorridos diarios y semanales
- [ ] Flujo de geolocalización de principio a fin

#### Geocercas y alertas

- [ ] Creación, consulta y actualización de geocercas

#### Grupos de búsqueda

- [ ] Mascotas perdidas y grupos de búsqueda
- [x] Creación de grupos de voluntarios — inspección de código; falta validación manual del flujo
- [x] Incorporación a un grupo — implementación y pruebas unitarias de servicio revisadas; validación HTTP/manual y concurrencia pendientes
- [x] Agregar/invitar un voluntario a un grupo — revisión estática de permisos, grupo cerrado, miembros revocados y notificación; validación HTTP/manual pendiente
- [x] Eliminar un voluntario de un grupo — revisión estática de permisos, protección del creador y revocación; validación HTTP/manual pendiente
- [x] Solicitud de reingreso a un grupo — implementación y pruebas unitarias de servicio revisadas; validación HTTP/manual y concurrencia pendientes

#### Chat de búsqueda

- [ ] Enviar mensajes a un grupo de voluntarios

#### Paseadores y paseos

- [ ] Marketplace de paseadores: perfiles, postulaciones y aprobación
- [ ] Solicitudes de paseo, cotizaciones, mensajes, posiciones y finalización

#### Notificaciones

- [ ] Notificaciones: correo, SMS, WhatsApp y WebSocket

#### Administración y panel

- [ ] Carga de usuarios
- [ ] Métricas y operaciones de administración

#### Datos de demostración

- [ ] Preparación de cuentas, mascotas y simulación GPS de demostración

### Detalle por revisión

Para cada elemento anterior, documentar el recorrido de usuario/API, las clases y tablas involucradas, el manejo de errores, permisos y dependencias. Para GPS y concurrencia, revisar además colas, hilos, orden de eventos, duplicados y persistencia bajo carga. Para las rutas, confirmar zona horaria, límites de ventana, orden cronológico y reglas de agregación diaria/semanal.

## Revisión: creación de grupos de voluntarios

**Estado:** implementación inspeccionada; faltan validación manual con usuario y base de datos.

### Recorrido actual

1. El formulario `searchGroupForm` pide mascota, nombre, visibilidad, descripción, zona y ciudad.
2. El navegador envía `POST /api/search-groups` como JSON.
3. El controlador exige usuario autenticado, busca la mascota y permite crear el grupo al dueño de la mascota o a un administrador.
4. `VolunteerService` aplica el límite de grupos del plan, normaliza visibilidad, guarda el grupo y agrega al creador como miembro activo.
5. Si se envían IDs de voluntarios, el servicio los agrega y les envía notificación. El formulario web actual no envía esos IDs.
6. Tras guardar, intenta notificar a voluntarios; se devuelve el grupo creado con HTTP 201.

### Código y datos involucrados

- Controlador: `SearchGroupController` (`POST /api/search-groups`, JSON o multipart).
- Lógica: `VolunteerService.createSearchGroupForLostPet`.
- Entidad/repositorio: `SearchGroup`, `SearchGroupRepository`, y membresías `Volunteer`/`VolunteerRepository`.
- Límite de creación: `PlanPolicy.searchGroups`.
- Interfaz actual: `searchGroupForm` en `static/index.html` y `createSearchGroup` en `static/app.js`.
- Esquema vigente: `search_group` y `volunteer`.

### Hallazgos y decisiones

- **Decisión:** se permite crear grupos para mascotas aunque no estén marcadas como `LOST`; el grupo de voluntarios no exige ese estado.
- El backend exige un nombre no vacío de hasta 255 caracteres y lo recorta. La visibilidad se normaliza y limita a `PRIVATE` o `PUBLIC`.
- `status` es el estado del grupo, no de la mascota. Al crear, siempre es `ACTIVE`; al actualizar, solo se acepta `ACTIVE` o `CLOSED`, y solo el creador puede cambiarlo.
- **Decisión implementada:** quien crea el grupo siempre queda como miembro activo. `Volunteer` es la única fuente de verdad para membresías; `SearchGroup.volunteers` fue eliminado y el mapper obtiene miembros y conteos desde `VolunteerRepository`.
- **Pendiente de migración física:** `schema.sql` ya no crea `searchgroup_volunteers`, pero tampoco contiene un `DROP TABLE` para instalaciones existentes. La tabla antigua puede seguir en bases ya inicializadas; confirmar si se conserva temporalmente o se elimina mediante una migración explícita.
- La notificación de creación se publica después de guardar el grupo y sus membresías; un listener asíncrono consulta y notifica únicamente a miembros activos de ese grupo.
- Los eventos de geocerca ya se enviaban desde `GeofencingService` a otro bean con `@Async`. Ahora ese servicio notifica únicamente membresías activas de grupos activos asociados a la mascota.
- Al marcar una mascota como perdida, antes solo se notificaba al dueño. Ahora también se notifica de forma asíncrona a los miembros activos de sus grupos activos.
- El aviso al dueño cuando alguien se une al grupo ahora se publica después del commit y lo procesa un listener asíncrono separado; ya no depende de una llamada interna a un método `private @Async`.

### ¿Para qué serviría el estado del grupo?

`SearchGroup.status` controla descubrimiento, joins y envío de mensajes; `Volunteer.active` controla si la membresía está activa y `Volunteer.revoked` distingue una expulsión de una salida voluntaria.

Un estado de grupo puede servir para cerrar o archivar una coordinación cuando termina (por ejemplo, búsqueda finalizada, cancelada o duplicada), aunque la mascota siga existiendo y el historial del grupo deba conservarse. Como ahora se permitirá crear grupos para mascotas no perdidas, el estado de la mascota no alcanza para representar el ciclo de vida de cada grupo.

**Decisión aprobada:** crear todos los grupos como `ACTIVE`; solo el creador puede cambiar su estado a `CLOSED`. Los grupos cerrados conservan el historial, salen del descubrimiento y no aceptan nuevas membresías ni mensajes. Los miembros autorizados todavía pueden leer mensajes anteriores. Solo el creador puede reabrirlos (`ACTIVE`). No se ofrece selector de estado durante la creación.

### Validación manual pendiente

- [ ] Dueño crea grupo para su mascota perdida y recibe HTTP 201.
- [ ] Usuario no dueño recibe rechazo al intentar crear grupo para mascota ajena.
- [ ] Administrador puede crear grupo para mascota ajena.
- [ ] Verificar que se permite crear el grupo para una mascota que no está marcada como perdida.
- [ ] Verificar que el backend rechaza nombres vacíos/ausentes, visibilidad inválida y límite de grupos del plan.
- [ ] Confirmar que el creador siempre queda como miembro activo y revisar las notificaciones al crear el grupo.
- [ ] Confirmar que solo miembros activos del grupo reciben la notificación de creación.
- [ ] Confirmar que alertas de geocerca y eventos de mascota perdida llegan a miembros activos de grupos activos relacionados con esa mascota.
- [ ] Confirmar que solo el creador puede cerrar/reabrir y que un grupo cerrado no se descubre, no acepta nuevos miembros ni mensajes.
- [ ] Confirmar que miembros autorizados conservan lectura del historial del chat.

### Proyecto separado

- [ ] Servicio Transit (`../transit`): revisión separada pendiente. Su README indica que no heredó autenticación FiruApp y que las notificaciones solo se registran en logs.

## Revisión: ingesta GPS

**Estado:** correcciones de implementación aplicadas para consulta de terminal, ACK, colas, validación y orden de posiciones. No se ejecutaron pruebas en este turno; saturación, PostgreSQL real, cierre bajo carga y compatibilidad por modelo de dispositivo siguen pendientes.

### Recorrido actual

1. `GpsTcpServer` acepta conexiones Netty y `GpsProtocolDetector` selecciona Topin o el flujo V41/JT808/ASCII.
2. Los terminales asociados se precargan en caché durante el inicio. JT808 resuelve un cache miss con un executor limitado y procesa la respuesta al terminar la consulta. Topin consulta solo la caché y falla cerrado durante el miss mientras programa la recarga; ese primer login puede requerir reconexión.
3. La persistencia se delega al executor GPS o a la cola opcional en memoria. JT808, Topin y ASCII esperan el resultado antes de enviar un ACK positivo para una posición; fallos de escritura o capacidad lo retienen. Tramas descartadas deliberadamente por validación/orden se reconocen para evitar reintentos inútiles.
4. `GpsIngestionService` valida fix GPS, coordenadas, antigüedad y orden estricto contra la última ubicación guardada. Solo después guarda `Location` y solicita evaluación de geocerca.
5. Los límites por defecto son 10 minutos de antigüedad y 5 minutos futuros; el chequeo de timestamp y coordenadas es común a V41, Topin y ASCII.

### Hallazgos

- **Mitigado — entrega/ACK:** un ACK positivo de ubicación ocurre después de persistir o de rechazar deliberadamente el fix; error de base de datos, saturación y apagado sin drenaje no se confirman como éxito. Hay contadores de posiciones entregadas al servicio, guardadas, rechazadas, fallidas y descartes. El listener cierra clientes/event loops antes de drenar. La cola opcional sigue siendo volátil: un cierre abrupto puede perder datos y el drenaje espera hasta 30 segundos.
- **Mitigado — validez y orden:** se rechazan `gpsValid=false`, coordenadas inválidas, puntos mayores de 10 minutos o más de 5 minutos en el futuro, y marcas de tiempo duplicadas o anteriores a la última guardada. Un bloqueo por mascota serializa esta comprobación en una instancia; varias réplicas aún requieren coordinación de base de datos.
- **Mitigado — acceso a BD desde Netty:** la caché se precarga al iniciar. JT808 espera un lookup acotado fuera del event loop; Topin falla cerrado en cache miss y recarga en segundo plano. La primera conexión Topin durante un miss puede rechazarse.

### Métodos y componentes revisados

| Método/componente | Motivo |
| --- | --- |
| `GpsProtocolDetector` y `TopinSessionHandler.channelRead0` | Selección de protocolo, login y admisión Topin desde caché sin SQL en Netty. |
| `GpsMessageHandler.channelRead0`, `handleJt808Reply`, `persistLocation`, `enqueueDecodedLocation`, `sendAsciiAckAfterProcessing` | Orden entre admisión, ACK diferido, validación y encolado. |
| `GpsIngestionService.processDecodedLocation`, `isKnownTerminalId`, `isKnownTerminalIdAsync`, `submitGpsTask`, `enqueueMessage`, `consumeQueuedMessages`, `persistDecodedLocation`, `stopMessageQueue` | Caché, límites, resultados, descartes, orden y drenaje. |
| `V41ProtocolDecoder.decodeLocation` y `TopinLocationHandler.channelRead0` | Diferencias de validación y ACK entre protocolos antes/después de guardar. |
| `GpsTcpServer.shutdown` | Cierre del socket de escucha/clientes/event loops antes de detener los consumidores GPS. |
| `AlertConfig.gpsExecutor`, `AlertConfig.gpsTerminalLookupExecutor` y `GpsCacheConfig.gpsPetByTerminalIdCache` | Capacidad de ejecutores y comportamiento de caché. |

### Decisiones pendientes

- Confirmar con cada modelo de terminal que la respuesta diferida conserva su temporización/reintentos; los fallos de infraestructura se responden con error JT808 o sin ACK Topin/ASCII.
- Definir si se requiere cola durable; la cola en memoria conserva pérdida posible ante terminación abrupta.
- Si se despliega más de una instancia, serializar el control de timestamp por mascota en PostgreSQL para cerrar carreras entre procesos.

### Validación pendiente

- [ ] Cache miss/expiración con PostgreSQL lento mientras reciben datos varios terminales; confirmar que Netty sigue procesando.
- [ ] Saturación de executor y cola; comprobar ACK negativo/omitido y comparar recibidos, guardados, fallidos y descartados.
- [ ] Error de base de datos y apagado con cola ocupada; comprobar drenaje dentro del timeout y contabilización de lo no guardado.
- [ ] Fix V41 inválido, fuera de rango, duplicado, atrasado y fuera de orden; confirmar que no se guarda ni dispara geocerca.
- [ ] Ejecución de carga con perfiles de terminal y configuración de producción.

## Métodos marcados como `REVIEWED`

La marca indica que se inspeccionó el método como parte de la revisión del flujo de grupos y membresías. La columna de motivo registra qué responsabilidad o regla se comprobó; no sustituye las pruebas de integración ni la validación manual pendiente.

### `SearchGroupController`

| Método | Motivo de revisión |
| --- | --- |
| `create` | Revisar la entrada JSON y confirmar que delega la creación en el flujo común. |
| `createWithImage` | Revisar la entrada multipart y el tratamiento opcional de la imagen al crear. |
| `createSearchGroup` | Comprobar autenticación, existencia y propiedad de la mascota, acceso de administrador, respuesta HTTP y delegación al servicio. |
| `update` | Revisar autenticación, actualización JSON y selección del DTO de respuesta. |
| `updateWithImage` | Revisar autenticación, actualización multipart y propagación de la imagen opcional. |
| `close` | Comprobar que el controlador delega el cambio de estado al servicio y responde con el grupo actualizado. |
| `join` | Revisar códigos HTTP y mensajes para miembro existente, ingreso, reactivación y solicitud de reingreso pendiente. |
| `addVolunteer` | Comprobar que el usuario autenticado se entrega al servicio para autorizar agregar a otro miembro. |
| `pendingRejoinRequests` | Comprobar que la consulta de solicitudes pasa por el servicio, que restringe el acceso al creador. |
| `approveRejoinRequest` | Revisar que la aprobación usa el usuario autenticado y el grupo/solicitud indicados. |
| `rejectRejoinRequest` | Revisar que el rechazo usa el usuario autenticado y el grupo/solicitud indicados. |
| `removeVolunteer` | Comprobar la delegación de la revocación con la identidad del usuario que la solicita. |
| `leave` | Comprobar que el miembro se identifica desde la sesión y no desde un ID arbitrario del cliente. |

### `VolunteerService`

| Método | Motivo de revisión |
| --- | --- |
| `joinSearchGroup(Long, Long)` | Revisar la compatibilidad del método antiguo y confirmar que delega en el resultado detallado del join. |
| `joinSearchGroupWithResult` | Revisar bloqueo del grupo, grupos cerrados, acceso, membresía duplicada, reactivación y tratamiento separado de miembros revocados. |
| `requestVolunteerRejoin` | Comprobar que reutiliza solicitudes pendientes y crea/publica una nueva solicitud cuando corresponde. |
| `canAccessSearchGroup` | Revisar reglas de acceso según rol, visibilidad, estado, propiedad de mascota, creación y membresía activa. |
| `leaveSearchGroup` | Comprobar que dejar el grupo desactiva la membresía voluntariamente y que el creador no puede dejar de ser miembro activo. |
| `createSearchGroupForLostPet` (sobrecarga antigua) | Revisar que la firma posicional obsoleta conserva compatibilidad y delega en el comando de creación. |
| `createSearchGroupForLostPet` (con `SearchGroupCreationCommand`) | Revisar validación de nombre/visibilidad/estado, creación del grupo, membresía activa del creador y publicación de eventos. |
| `updateSearchGroup` (sin imagen) | Comprobar que la actualización JSON delega en la implementación común sin imagen. |
| `updateSearchGroup` (con imagen) | Revisar permisos de gestión, validación de campos y que solo el creador cambie el estado del grupo. |
| `addVolunteer` | Revisar permisos, grupos cerrados, prevención de bypass para miembros revocados y notificación al crear/reactivar una membresía. |
| `removeVolunteer` | Comprobar permisos, protección del creador y que la eliminación marca al miembro como revocado para exigir aprobación al volver. |
| `listPendingRejoinRequests` | Revisar que solo el creador pueda consultar solicitudes pendientes del grupo. |
| `approveRejoinRequest` | Comprobar autorización exclusiva del creador, estado pendiente, reapertura de membresía y registro de resolución. |
| `rejectRejoinRequest` | Comprobar autorización exclusiva del creador y cierre de la solicitud sin reactivar la membresía. |
| `requirePendingRejoinRequest` | Revisar que la solicitud pertenece al grupo indicado y que no se procese dos veces. |
| `requireGroupCreator` | Comprobar la regla común que limita decisiones de reingreso al creador. |
| `toRejoinRequestDto` | Revisar la transformación de la solicitud y sus datos de voluntario al DTO de API. |
| `addVolunteerMembership` | Comprobar la creación/reactivación de una única membresía activa y sus marcas temporales. |
| `requireSearchGroupForUpdate` | Revisar la carga del grupo con bloqueo para serializar cambios concurrentes de membresía/estado. |
| `requireGroupManager` | Comprobar la regla de autorización compartida para operaciones de gestión del grupo. |
| `closeSearchGroup` | Revisar que solo el creador puede cerrar el grupo. |
| `isCreator` | Comprobar la comparación segura de identidad usada por las reglas del creador. |
| `notifyVolunteersInGroup` | Revisar envío a miembros activos, destinatarios y ejecución mediante el executor asíncrono. |

### `VolunteerNotificationService`

| Método | Motivo de revisión |
| --- | --- |
| `notifyGroupMembersWhenCreated` | Comprobar que avisa solo a miembros activos del grupo y que el listener corre asíncronamente después del commit. |
| `notifyOwnerWhenVolunteerJoins` | Revisar que el aviso de ingreso va al dueño de la mascota y solo para una membresía activa, después del commit. |
| `notifyVolunteerWhenAdded` | Comprobar que el voluntario agregado recibe el aviso y que un listener separado evita la llamada interna que anulaba `@Async`. |
| `notifyCreatorAboutRejoinRequest` | Revisar que la solicitud pendiente notifica al creador, con datos de decisión, después del commit y en forma asíncrona. |
| `notifyVolunteerAboutRejoinDecision` | Comprobar que quien pidió volver recibe la decisión de aprobación o rechazo después del commit. |
| `notifyPetGroupMembers` | Revisar que alertas de mascota se envían a miembros activos de grupos activos asociados con esa mascota. |

### Repositorios

| Archivo y método | Motivo de revisión |
| --- | --- |
| `SearchGroupRepository.findByIdForUpdate` | Comprobar el bloqueo pesimista usado para serializar operaciones concurrentes sobre el mismo grupo. |
| `VolunteerRejoinRequestRepository.findFirstByVolunteerIdAndStatusOrderByCreatedAtDesc` | Revisar la búsqueda de una solicitud pendiente para impedir duplicados lógicos al reintentar. |
| `VolunteerRejoinRequestRepository.findByIdAndSearchGroupId` | Comprobar que una decisión solo localiza solicitudes pertenecientes al grupo de la ruta. |
| `VolunteerRejoinRequestRepository.findWithDetailsById` | Revisar la carga de usuario, grupo y creador que necesita la notificación al creador. |
| `VolunteerRejoinRequestRepository.findBySearchGroupIdAndStatusOrderByCreatedAtAsc` | Comprobar que la bandeja obtiene las solicitudes por grupo, estado y orden de creación con relaciones necesarias. |

### `src/main/resources/static/app.js`

| Función | Motivo de revisión |
| --- | --- |
| `joinSearchGroup` | Revisar acciones de aprobar/rechazar, confirmación de rechazo, join, mensajes de respuesta y actualización de la interfaz. |
| `loadPendingRejoinRequests` | Comprobar la consulta/renderizado de solicitudes y el escape de datos presentados en la lista del creador. |
| `renderSearchGroups` | Revisar estados vacíos, grupos visibles y controles que aparecen para unirse, cerrar o gestionar solicitudes según permisos/estado. |

## Registro de decisiones

| Funcionalidad | Estado | Decisión | Dependencias que deben permanecer | Notas |
| --- | --- | --- | --- | --- |
| Base mínima de la aplicación | Pendiente | — | — | — |
| Registro y autenticación | Pendiente | — | — | — |
| Mascotas y rastreadores | Pendiente | — | — | — |
| Acceso temporal | Pendiente | — | — | — |
| GPS y ubicaciones | Correcciones de ingesta implementadas; validación pendiente | ACK positivo tras persistencia/rechazo deliberado; validación común; lookup asíncrono y drenaje limitado | Listener, decodificadores, handler, servicio GPS, caché, ejecutores, cola opcional, `Location` y geocercas | B16–B18 mitigadas parcialmente; falta carga, BD real, varias réplicas y validación por terminal |
| Geocercas y alertas | Pendiente | — | — | — |
| Comunidad de búsqueda | Pendiente | — | — | — |
| Marketplace y paseos | Pendiente | — | — | — |
| Notificaciones | Pendiente | — | — | — |
| Métricas y administración | Pendiente | — | — | — |
| Transit (proyecto separado) | Pendiente | — | — | — |

## Resultado final

- **Ruta mínima de arranque:** pendiente de determinar.
- **Funcionalidades conservadas:** pendiente de revisión.
- **Funcionalidades retiradas o aisladas:** pendiente de revisión.
- **Configuración y documentación por actualizar:** pendiente de revisión.
