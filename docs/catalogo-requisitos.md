# Catálogo de requisitos de FiruApp

Este catálogo reúne las funcionalidades del repositorio PetTracker y sus requisitos de calidad. Permite relacionar cada regla con su implementación, definir cómo comprobarla y mantener visibles las decisiones de producto pendientes.

Fecha de referencia: 21 de noviembre de 2026. Base: commit `830fffe` y los cambios locales presentes durante la revisión. El alcance de implementación de este catálogo es el backend FiruApp, su panel web estático, GPS, configuración, esquema SQL y pruebas de este repositorio. Los requisitos RF-TRA describen Transit, que ahora vive en el proyecto Spring Boot hermano `../transit`; se deben verificar contra ese proyecto, no contra este código. El frontend React mencionado en la documentación pertenece a otro repositorio y no está verificado aquí.

El catálogo registra hallazgos de inspección estática. Los criterios de aceptación describen comprobaciones que deben realizarse; la existencia de código o de un archivo de prueba no acredita por sí sola que esas comprobaciones hayan pasado. Después de preparar el inventario, se ejecutaron 15 pruebas unitarias de `VolunteerServiceTest` para el flujo de membresía; no se ejecutaron pruebas de carga ni de proveedores externos.

El inventario contiene **152 requisitos funcionales** —141 vinculados a capacidades del código, 8 futuros o fuera del alcance actual y 3 compromisos externos— y **57 requisitos no funcionales**. Incluye **11 decisiones pendientes**, **1 resuelta**, **18 brechas priorizadas** y referencias a **18 clases de prueba**. La [matriz de trazabilidad](matriz-trazabilidad-api.md) cubre las 78 operaciones HTTP distintas de los 11 controladores de este backend con rutas; excluye las rutas del proyecto Transit separado.

## Convenciones y estados

Cada requisito tiene un identificador estable, nombre, actor o regla, criterio de aceptación, estado y referencias. `RF` identifica una funcionalidad; `RNF`, una propiedad de calidad. Los identificadores se conservan aunque cambie el texto; los requisitos retirados se marcan como tales y sus números no se reutilizan.

| Estado | Significado |
| --- | --- |
| OBS | Comportamiento observable en el código; requiere validación funcional y de producto. |
| PAR | Implementación parcial o con una brecha concreta indicada en el requisito. |
| DEC | Regla pendiente de decisión; no debe tratarse como comportamiento aprobado. |
| PEN | Capacidad prevista en documentación, sin implementación operativa identificada. |
| PROP | Requisito de calidad propuesto para completar la especificación; falta acordar su objetivo. |
| EXT | Compromiso comercial u operativo cuya ejecución está fuera del backend. |

En las referencias, `T01`–`T18` remiten al inventario de pruebas al final. `—` significa que no se identificó una prueba específica para ese criterio. Una referencia de prueba puede cubrir solo parte de la fila. `D01`–`D12` identifican decisiones registradas, que pueden estar pendientes o resueltas, y `B01`–`B14`, brechas observadas.

## Actores y permisos

| Actor | Alcance |
| --- | --- |
| Visitante | Registro, inicio de sesión, catálogo de paseadores, solicitud de paseador y aceptación por token. |
| Usuario autenticado | Identidad comprobada mediante JWT; su rol no implica acceso a todos los recursos. |
| Dueño de mascota | Usuario relacionado como propietario de una mascota concreta. |
| Persona autorizada | Usuario con acceso permanente compartido a una mascota; no adquiere permisos de edición. |
| Cuidador temporal | Usuario que aceptó un acceso vigente; está sujeto a fechas, horario y permisos concedidos. |
| Voluntario | Usuario con membresía en un grupo de búsqueda; la membresía puede estar inactiva. |
| Creador de grupo | Usuario registrado como creador; puede diferir del dueño de la mascota. |
| Paseador | Usuario con rol WALKER y perfil; la aprobación determina su aparición en el catálogo. |
| Administrador | Usuario con rol ADMIN; accede a la administración de este backend y tiene excepciones de planes. Transit es un servicio separado y hoy no heredó esta autenticación. |
| Dispositivo GPS | Terminal identificado por el protocolo; no utiliza la autenticación JWT del navegador. |
| Sistema | Tareas programadas, ingestión, notificaciones y procesos de operación. |

## Requisitos funcionales

### Autenticación y sesión

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-AUT-001 | Registrar cuenta | Visitante registra nombre, correo, contraseña y teléfono; rol USER. | Correo nuevo crea una cuenta USER no verificada y devuelve 201 con el resultado del envío; el JWT solo se emite después de confirmar el correo. `GET /api/auth/verify-email?token=...` consume el token y `POST /api/auth/resend-verification` solicita un nuevo correo. Si falla el envío inicial, la cuenta queda creada y la respuesta indica cómo reintentar. Correo repetido sin distinguir mayúsculas se rechaza. Validación exhaustiva de campos: RNF-SEG-008. | OBS | [AuthController][auth] `register`; [UserRepository][users-repo]; — |
| RF-AUT-002 | Iniciar sesión | Usuario presenta correo y contraseña. | Credenciales válidas devuelven JWT; inválidas no ejecutan la preparación de datos demo. | OBS | [AuthController][auth] `login`; T09 parcial |
| RF-AUT-003 | Validar sesión JWT | API protegida comprueba firma y vencimiento del bearer token. | Token válido establece identidad y rol; ausente, alterado o vencido no habilita una ruta protegida. | OBS | [JwtProvider][jwt], [JwtAuthFilter][jwt-filter], [SecurityConfig][security]; — |
| RF-AUT-004 | Consultar identidad actual | Usuario consulta su identidad, rol, plan y perfil de paseador opcional. | GET /api/me devuelve datos del usuario autenticado; nunca contraseña. | OBS | [WalkerMarketplaceService][market] `currentUser`; — |
| RF-AUT-005 | Cerrar sesión del navegador | Usuario elimina su sesión local. | Cerrar sesión borra token y estado de la interfaz; el JWT emitido conserva su validez hasta vencer. Revocación del servidor pendiente en RNF-SEG-011. | OBS | [app.js][ui] `logout`; — |

### Planes y límites

Los siguientes valores son el comportamiento actual, sujeto a D02. Las ventanas de historial limitan consultas; no constituyen una política de borrado de datos.

| Beneficio | BASIC | FAMILY | PREMIUM | ADMIN |
| --- | --- | --- | --- | --- |
| Historial consultable | 30 días | 90 días | 365 días | Sin restricción de antigüedad; consultas por defecto de 365 días |
| Personas con acceso permanente por mascota | 5 | 10 | Servicio permite cantidad ilimitada, aunque PlanPolicy devuelve 20 | Sin límite |
| Geocercas por mascota | 3 | 5 | 10 | Exento de límite comercial |
| Grupos creados por cuenta | 1 | 3 | 5 | Sin límite |
| Acceso temporal | No | Sí | Sí | Sí |
| Horario temporal recurrente | No | No | Sí | Sí en backend |
| Exportación CSV y PDF | No | No | Sí | Sí |
| Resumen de recorridos | Endpoint semanal disponible | Endpoint semanal disponible | Endpoint semanal disponible; comparación mensual no identificada | Endpoint semanal disponible |

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-PLA-001 | Asignar plan inicial | Registro admite el plan solicitado y usa BASIC si falta. | Registrar sin plan asigna BASIC; definir si FAMILY/PREMIUM requieren compra o aprobación antes de habilitarlos. | DEC | [AuthController][auth] `register`, [PlanPolicy][plans]; D02; — |
| RF-PLA-002 | Cambiar plan de usuario | Administrador actualiza BASIC, FAMILY o PREMIUM. | Actualización cambia el plan y expulsa la entrada local de caché del usuario; plan nulo se convierte en BASIC. Definir efectos de bajar de plan. | OBS | [UserPlanController][user-plans] `updatePlan`, [UserService][user-service]; D02; — |
| RF-PLA-003 | Limitar antigüedad del historial | Consulta de historial usa el plan del usuario que consulta. | Fechas anteriores a la ventana permitida se rechazan con 403; definir si el cuidador debe heredar el plan del dueño. | OBS | [PlanPolicy][plans] `requireHistoryWindow`, [PetService][pets]; D03; — |
| RF-PLA-004 | Limitar personas autorizadas | Dueño comparte mascotas dentro de su cuota. | Compartir dos veces no consume otra plaza; BASIC/FAMILY rechazan al superar 5/10. Resolver 20 frente a ilimitado en PREMIUM. | PAR | [PetAccessService][shares] `share`, [PlanPolicy][plans]; D02; — |
| RF-PLA-005 | Limitar creación de grupos | Cuenta no administradora respeta el límite de grupos creados. | Crear el siguiente grupo después de 1/3/5 se rechaza; el conteo actual incluye todos los estados. | OBS | [VolunteerService][groups] `createSearchGroupForLostPet`; — |
| RF-PLA-006 | Limitar geocercas por mascota | Dueño respeta 3/5/10 zonas según plan; administrador exento. | Al alcanzar la cuota, otra creación se rechaza con 403 y no guarda una zona. | OBS | [GeofencingService][geofence] `enforceGeofenceLimit`; — |
| RF-PLA-007 | Diferenciar resúmenes por plan | Oferta comercial diferencia consulta diaria, semanal y comparación mensual. | Definir los resultados por plan; implementar y comprobar comparación mensual y controles de acceso acordados. Actualmente el endpoint semanal no diferencia planes. | PAR | [Planes y límites][commercial], [PetWalkDistanceService][distance], [PetService][pets]; D02; — |

### Mascotas

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-MAS-001 | Crear mascota | Dueño crea mascota; administrador puede elegir propietario. | JSON y multipart crean la mascota vinculada al dueño correcto; un usuario común no puede asignarla a otro ownerId. | OBS | [PetController][pet-api], [PetService][pets] `create`; T02 parcial |
| RF-MAS-002 | Listar mascotas accesibles | Incluye propias, compartidas y temporales vigentes; administrador ve todas. | Devuelve mascotas sin duplicados y aplica page/size. Las temporales vencidas o sin aceptar no aparecen. | OBS | [PetService][pets] `listForUser`, [PetRepository][pets-repo]; — |
| RF-MAS-003 | Consultar ficha de mascota | Solo usuario con acceso a la mascota. | ID inexistente devuelve 404 y usuario sin acceso recibe rechazo. Los datos derivados de ubicación deben respetar RF-TMP-009. | PAR | [PetService][pets] `getForCurrentUser`, [PetMapper][pet-map]; B03; — |
| RF-MAS-004 | Editar mascota | Dueño o administrador modifica datos; solo administrador reasigna propietario. | Compartido/cuidador no edita; actualización sin imagen conserva la existente. | OBS | [PetService][pets] `update`; T01, T02 parciales |
| RF-MAS-005 | Eliminar mascota | Dueño o administrador solicita eliminación. | Tercero es rechazado; definir tratamiento de historial, alertas, grupos y paseos asociados antes de garantizar borrado con dependencias. | PAR | [PetService][pets] `delete`, [esquema SQL][schema]; D12; — |
| RF-MAS-006 | Subir imagen de mascota | Alta o edición admite JPEG, PNG, WebP y GIF con límite configurable. | Imagen válida reemplaza bytes y metadatos; exceso devuelve 413 antes de reemplazar la imagen anterior. | OBS | [PetService][pets] `setImageIfPresent`, [ImageUploadLimits][images]; T01, T02, T03 |
| RF-MAS-007 | Descargar imagen de mascota | Usuario con acceso descarga la imagen guardada. | Devuelve bytes y tipo de contenido; imagen inexistente responde 404; tercero no puede obtenerla. | OBS | [PetController][pet-api] `getImage`, [PetService][pets]; T01 parcial |
| RF-MAS-008 | Vincular terminal GPS | Dueño/admin registra IMEI y terminalId como campos distintos. | Ingestión busca terminalId exacto, incluidos ceros iniciales; altas/cambios/bajas invalidan las entradas de terminal implicadas. | OBS | [PetService][pets], [PetMapper][pet-map], [GpsIngestionService][ingestion]; T12, T14 parciales |
| RF-MAS-009 | Marcar mascota perdida | Dueño/admin cambia estado a LOST y activa notificación. | Mascota queda LOST y el aviso identifica a la mascota real; los avisos actuales contienen duplicados y nombres fijos. | PAR | [PetService][pets] `markPetAsLost`, [LostPetNotificationService][lost-notify]; T07 divergente; B06 |
| RF-MAS-010 | Marcar mascota encontrada | Dueño/admin devuelve estado a ACTIVE y avisa al dueño. | Estado y aviso reflejan la recuperación. No se cierran grupos automáticamente; decidir ese efecto en D04. | OBS | [PetService][pets] `markPetAsFound`, [LostPetNotificationService][lost-notify]; — |

### Acceso permanente compartido

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-ACC-001 | Compartir mascota | Dueño/admin agrega un usuario existente por correo. | Rechaza correo ausente, usuario inexistente y dueño como destinatario; repetición devuelve la relación existente. | OBS | [PetAccessService][shares] `share`, [PetAccessController][shares-api]; — |
| RF-ACC-002 | Listar personas autorizadas | Dueño/admin ve destinatarios de una mascota. | Lista ordenada por fecha de autorización; persona compartida no administra la lista. | OBS | [PetAccessService][shares] `list`; — |
| RF-ACC-003 | Revocar acceso permanente | Dueño/admin elimina una autorización concreta. | Operación repetida no falla por relación ausente; futuras lecturas se deniegan salvo que exista otra autorización válida. | OBS | [PetAccessService][shares] `revoke`; — |
| RF-ACC-004 | Consultar recursos compartidos | Autorizado permanente consulta ficha, imagen, ubicación, historial y geocercas. | Puede leer recursos autorizados sin editar mascota ni administrar accesos. La inclusión de alertas está pendiente: el listado actual contempla dueño y acceso temporal. | PAR | [PetAccessService][shares], [PetService][pets], [AlertService][alerts]; D03; — |

### Acceso temporal para cuidadores

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-TMP-001 | Crear acceso temporal | Dueño FAMILY/PREMIUM o administrador concede acceso con inicio y fin. | Requiere inicio anterior al vencimiento y al menos un permiso; BASIC recibe 403. | OBS | [TemporaryPetAccessService][temporary] `create/validateRequest`; — |
| RF-TMP-002 | Resolver o crear cuidador | Destinatario se selecciona por userId o correo. | userId existente tiene prioridad; correo desconocido crea USER/BASIC con hash BCrypt de una contraseña aleatoria. Definir incorporación y cambio inicial de contraseña. | OBS | [TemporaryPetAccessService][temporary] `resolveUser/create`; D03; — |
| RF-TMP-003 | Enviar invitación temporal | Sistema prepara correo con token y credenciales iniciales si corresponden. | Entrega enlace utilizable desde correo; hoy se construye una ruta relativa y no se comprueba entrega SMTP. | PAR | [TemporaryPetAccessService][temporary] `create`, [NotificationService][notify]; B13; — |
| RF-TMP-004 | Aceptar invitación | Poseedor del token utiliza GET o POST público de aceptación. | Token válido registra aceptación y elimina su hash; token consumido no se reutiliza; acceso inactivo/vencido se rechaza. Definir aceptación por identidad y uso de GET. | OBS | [TemporaryPetAccessController][temporary-api], [TemporaryPetAccessService][temporary] `accept`; D03; — |
| RF-TMP-005 | Listar accesos temporales | Dueño/admin consulta accesos de una mascota. | Lista inicio, fin, permisos, aceptación y si está actualmente vigente, ordenada por inicio. | OBS | [TemporaryPetAccessService][temporary] `list/toDto`; — |
| RF-TMP-006 | Revocar acceso temporal | Dueño/admin revoca una concesión de su mascota. | Guarda active=false y revokedAt; la autorización deja de servir sin esperar al proceso periódico. | OBS | [TemporaryPetAccessService][temporary] `revoke/findActive`; — |
| RF-TMP-007 | Aplicar vigencia y vencimiento | Solo accesos aceptados y dentro de sus fechas habilitan consultas. | Antes del inicio y después del fin se deniega acceso; tarea cada 60 segundos desactiva concesiones vencidas. | OBS | [TemporaryPetAccessService][temporary] `isCurrentlyActive/expireAccesses`; — |
| RF-TMP-008 | Programar acceso recurrente | PREMIUM/admin define días y horario diario en la zona de la app. | Fuera del día u horario no se autoriza; exige hora inicial menor que final, por lo que no admite cruces de medianoche. | OBS | [TemporaryPetAccessService][temporary] `validateRequest/isCurrentlyActive`; D03; — |
| RF-TMP-009 | Respetar permisos temporales por dato | Ubicación, historial y alertas son permisos independientes. | Desactivar un permiso impide obtener ese dato por cualquier endpoint o campo derivado. Hay rutas que usan permiso general o de ubicación para datos históricos. | PAR | [TemporaryPetAccessService][temporary], [PetService][pets], [PetMapper][pet-map], [AlertService][alerts]; B03, B04; — |

### Dispositivos GPS e ingestión

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-GPS-001 | Escuchar dispositivos TCP | Sistema abre listener configurado, por defecto 5000. | Al iniciar acepta conexiones y selecciona Topin o V41/JT808/ASCII; al cerrar libera canal y event loops. | OBS | [GpsTcpServer][gps-server], [GpsProtocolDetector][gps-detector]; T11 parcial |
| RF-GPS-002 | Reconstruir tramas TCP | Sistema procesa fragmentos y varias tramas en una recepción. | No pierde bytes iniciales ni interpreta cortes TCP como límites del mensaje; límites de memoria pendientes en RNF-REN-007. | OBS | [V41FrameDecoder][v41-frame], [TopinFrameDecoder][topin-frame]; T11, T12 parciales |
| RF-GPS-003 | Decodificar V41 y JT808 | Admite cabecera clásica y 2019, escapes, checksum y longitud. | Tramas malformadas, cifradas o fragmentadas a nivel de subpaquete no soportado se rechazan; identidad BCD conserva ceros. | OBS | [V41ProtocolDecoder][v41-decoder]; T12, T16, T17 |
| RF-GPS-004 | Responder registro y heartbeat JT808 | Terminal registrado recibe respuesta compatible y secuencia por conexión. | Registro/autenticación/heartbeat/localización de desconocido devuelve fallo; no crea bucle de ACK ni ubicación. | OBS | [GpsMessageHandler][gps-handler], [Jt808Replies][jt-replies]; T12 |
| RF-GPS-005 | Procesar protocolo ASCII parcial | Admite tramas entre corchetes, hemisferios y comandos reconocidos. | LK/TKQ/TKQ2 generan ACK; UD_LTE/LK extraen batería validada; posiciones sin coordenadas no se guardan. No equivale a soporte de cualquier equipo ASCII. | OBS | [GpsMessageHandler][gps-handler]; T15, T11 parciales |
| RF-GPS-006 | Procesar sesiones Topin | Login de terminal conocido habilita perfiles de paquete explícitamente soportados. | Rechaza identidad desconocida/cambio de identidad; solo publica GPS válido no histórico, hasta 5 minutos futuro y 10 minutos antiguo. | OBS | [TopinSessionHandler][topin-session], [TopinLocationHandler][topin-location]; T11 parcial |
| RF-GPS-007 | Conservar telemetría de batería | Sistema extrae porcentaje y voltaje cuando existen. | Porcentaje 0–100 conserva extremos; ausente/inválido no se transforma en batería cero; extensión dañada no descarta posición válida. | OBS | [V41ProtocolDecoder][v41-decoder], [LocationMapper][location-map]; T15, T16, T17 |
| RF-GPS-008 | Interpretar hora del dispositivo | Zona por terminal JT808 y zona general de aplicación. | Hora UTC/local se convierte según configuración y conserva cambio de fecha; terminal con ceros iniciales utiliza su clave exacta. | OBS | [Jt808TimeZones][jt-time], [AppTimeService][time]; T05, T13, T14 |
| RF-GPS-009 | Persistir posiciones del collar | TerminalId registrado se asocia a mascota y genera Location. | Rechaza fix inválido, coordenadas fuera de rango, puntos >10 min antiguos o >5 min futuros y marcas duplicadas/anteriores a la última guardada; solo puntos aceptados activan geocercas. Verificar carreras entre réplicas. | PAR | [GpsIngestionService][ingestion] `persistDecodedLocation`, [V41ProtocolDecoder][v41-decoder], [TopinSessionHandler][topin-session]; B17; T17 parcial |
| RF-GPS-010 | Elegir modo de ingestión | Sistema usa executor directo o cola de memoria opcional. | La capacidad es configurable; rechazo/fallo de persistencia no recibe ACK positivo. Los consumidores drenan la cola opcional hasta 30 s al cerrar ordenadamente; la cola sigue siendo volátil. | PAR | [GpsIngestionService][ingestion], [application.yml][config], [GpsMessageHandler][gps-handler]; B16, RNF-CON-002; — |

### Ubicaciones e informes

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-LOC-009 | Registrar ubicación manual | Dueño/admin registra coordenadas, batería y fecha opcional. | Persiste sincrónicamente; falta de fecha usa hora de app y activa comprobación de geocercas. Valores geográficos requieren validación uniforme. | OBS | [PetService][pets] `addLocation`, [GpsIngestionService][ingestion]; T05 parcial; RNF-SEG-008 |
| RF-LOC-001 | Consultar últimas ubicaciones | Usuario consulta la última posición de mascotas propias y compartidas permanentes. | Devuelve una posición por mascota según timestamp; los accesos temporales actualmente no están en esta consulta agregada. | PAR | [LocationRepository][locations-repo] `findLastLocationsByUserId`, [PetService][pets]; B04; — |
| RF-LOC-002 | Consultar historial disponible | Consulta /locations usa ventana de plan y hasta 720 puntos. | Devuelve posiciones ordenadas dentro de la ventana; requiere corregir que se compruebe ubicación y no permiso de historial. | PAR | [PetService][pets] `getLocations`, [LocationService][locations]; B03; — |
| RF-LOC-003 | Consultar ruta reciente | Usuario autorizado consulta las últimas tres horas. | Respuesta ordenada ascendentemente y limitada a 720 puntos; acordar si el cuidador necesita permiso de historial. | OBS | [LocationService][locations] `getPetRouteLast3Hours`, [PetService][pets]; D03; — |
| RF-LOC-004 | Consultar ruta por fechas | Usuario con permiso de historial indica from/to ISO-8601. | Rechaza rango invertido o demasiado antiguo para su plan; devuelve hasta 720 puntos ascendentes. | OBS | [PetService][pets] `getRouteBetween`, [LocationService][locations]; T05 parcial |
| RF-LOC-005 | Resolver dirección y barrio | Sistema consulta Nominatim para una ubicación y conserva el resultado. | Devuelve ciudad, dirección, barrio/comuna y fuente; fallo/desactivación devuelve resultado no resuelto. El indicador resolved está condicionado a Medellín. | OBS | [NeighborhoodLookupService][neighborhood], [LocationService][locations]; D11; — |
| RF-LOC-006 | Calcular distancia semanal | Usuario con acceso consulta siete días de posiciones de paseos. | Suma tramos del mismo paseo, devuelve días sin actividad con 0 y usa lunes actual si falta weekStart; no calcula distancia a partir del GPS del collar. | OBS | [PetWalkDistanceService][distance]; RF-PLA-007, RF-TMP-009; — |
| RF-LOC-007 | Exportar historial CSV | PREMIUM/admin con permiso de historial descarga un rango. | Devuelve CSV con posiciones y metadatos; plan inferior recibe 403; definir límite de puntos y neutralización de fórmulas para hojas de cálculo. | PAR | [PetService][pets] `exportHistory/loadExportHistory`, [PetController][pet-api]; RNF-REN-003, RNF-SEG-009; — |
| RF-LOC-008 | Exportar historial PDF | PREMIUM/admin descarga reporte de posiciones. | Devuelve archivo PDF paginado; formato distinto de csv/pdf se rechaza. Definir soporte de caracteres, pues los no ASCII se sustituyen por ?. | PAR | [PetService][pets] `exportHistoryPdf/buildPdf`, [PetController][pet-api]; D11; — |

### Geocercas y alertas

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-GEO-001 | Crear zona circular | Dueño/admin define centro y radio en metros. | Exige centro y radio positivo, aplica cuota y guarda zona CIRCLE. | OBS | [GeofencingService][geofence] `createCircle`; — |
| RF-GEO-002 | Crear zona poligonal | Dueño/admin define al menos tres pares latitud/longitud. | Se cierra el anillo y guarda POLYGON; topología y límites geográficos quedan en RNF-SEG-008. | OBS | [GeofencingService][geofence] `createPolygon/buildPolygon`; — |
| RF-GEO-003 | Listar zonas de mascota | Usuario con acceso consulta todas las geocercas de la mascota. | Devuelve lista ordenada por ID; no restringe a una zona única. | OBS | [GeofencingService][geofence] `getByPet`; — |
| RF-GEO-004 | Eliminar todas las zonas | Dueño/admin elimina geocercas de una mascota. | Borra las zonas de esa mascota; sin zonas responde 404. | OBS | [GeofencingService][geofence] `deleteByPet`; — |
| RF-GEO-005 | Eliminar zona individual | Dueño/admin indica mascota y geocerca. | Rechaza una geocerca que pertenece a otra mascota y conserva las demás. | OBS | [GeofencingService][geofence] `deleteById`; — |
| RF-GEO-006 | Evaluar salida de zonas seguras | Sistema considera segura una posición dentro de cualquiera de las zonas. | Sin zonas no alerta; fuera de todas crea aviso. El borde de círculo se incluye y el de polígono no; acordar D06. | OBS | [GeofencingService][geofence] `isOutside/evaluateOutsideGeofence`; — |
| RF-GEO-007 | Evaluar al crear una zona | Nueva zona provoca evaluación de la última posición disponible. | Sin posición no falla; con posición evalúa el conjunto actual de zonas y puede crear alerta inmediatamente. | OBS | [GeofencingService][geofence] `checkLatestLocationForNewGeofence`; — |
| RF-ALT-001 | Registrar alerta de geocerca | Sistema guarda BOUNDARY con estado OPEN, mascota, fecha y coordenadas. | Una evaluación fuera de zona genera registro; no existe deduplicación por permanencia fuera, ver D06. | OBS | [GeofencingService][geofence], [AlertService][alerts] `createAlert`; T10 solo demo |
| RF-ALT-002 | Consultar alertas abiertas | Dueño, cuidador con canViewAlerts o administrador consulta alertas. | Devuelve OPEN en orden reciente; administrador consulta todas; cuidador vencido/revocado no conserva acceso. | OBS | [AlertService][alerts] `getOpenAlertsForCurrentUser`; — |
| RF-ALT-003 | Cerrar alerta | Solo dueño/admin cierra una alerta. | DELETE cambia OPEN a CLOSED sin borrar el registro; cuidador y tercero reciben rechazo. | OBS | [AlertService][alerts] `dismissForCurrentUser`; — |

### Grupos de búsqueda

La visibilidad pública de un grupo no elimina el requisito de iniciar sesión para usar sus endpoints. El permiso para consultar el grupo y el permiso para leer su chat son diferentes.

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-GRU-006 | Crear grupo de búsqueda | Dueño de mascota/admin crea grupo y el creador queda como miembro activo. | Exige petId accesible, aplica cuota y admite JSON/multipart. Solo admin puede incluir voluntarios iniciales desde la API. Se permite cualquier estado de la mascota; todo grupo nuevo queda `ACTIVE`. | OBS | [SearchGroupController][groups-api] `createSearchGroup`, [VolunteerService][groups]; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-008 | Configurar visibilidad | Gestor establece PRIVATE o PUBLIC; defecto PRIVATE. | Otro valor se rechaza; un usuario sin relación obtiene 404 al consultar un grupo privado. | OBS | [VolunteerService][groups] `normalizeVisibility/canAccessSearchGroup`; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-009 | Listar grupos descubribles | Usuario autenticado ve grupos públicos activos y grupos donde tiene membresía activa; admin ve todos. | Paginación hasta 100. Los grupos cerrados solo aparecen a miembros activos; el dueño sin membresía no obtiene todos por esta ruta. | OBS | [SearchGroupRepository][groups-repo] `findDiscoverableForUser`, [SearchGroupController][groups-api]; B12; — |
| RF-GRU-012 | Consultar detalle y representación pública | Usuario autorizado ve grupo; solo admin recibe campos completos del creador y listado incrustado de miembros. | DTO público omite cuentas de miembros/creador, pero conserva contacto del líder y WhatsApp; confirmar política de publicación. | OBS | [SearchGroupMapper][group-map], [SearchGroupController][groups-api] `getById`; D04; — |
| RF-GRU-001 | Unirse a un grupo público | Usuario autenticado puede crear su membresía en un grupo PUBLIC. | Sin membresía previa crea una activa y responde 201; miembro activo repetido responde 200; una persona revocada genera solicitud pendiente y responde 202. | OBS | [VolunteerService][groups] `joinSearchGroupWithResult`, [SearchGroupController][groups-api]; — |
| RF-GRU-017 | Repetir unión activa sin duplicar | Miembro activo vuelve a pulsar unirse. | Responde 200, alreadyMember=true y reactivated=false sin guardar otra membresía ni notificar; concurrencia pendiente en RNF-DAT-001. | OBS | [VolunteerService][groups] `joinSearchGroupWithResult`; T04 |
| RF-GRU-016 | Buscar candidatos a miembro | Solo administrador busca por nombre/correo a quienes no son miembros activos. | q de 2 a 100 caracteres; búsqueda sin distinguir mayúsculas, paginada hasta 20. El servicio permite gestores, pero el controlador impone ADMIN. | OBS | [SearchGroupController][groups-api] `memberCandidates`, [UserRepository][users-repo]; T04 solo servicio |
| RF-GRU-002 | Incorporar a un grupo privado | Dueño de mascota, creador o admin agrega un usuario mediante endpoint de miembros. | Agregar activa una membresía no revocada; una revocada debe solicitar reingreso y solo el creador decide. Un desconocido no puede autoinscribirse adivinando el ID. | OBS | [VolunteerService][groups] `addVolunteer/requireGroupManager`; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-010 | Listar mis grupos | Usuario consulta grupos donde tiene membresía activa. | Excluye membresías inactivas y aplica paginación; conserva grupos cerrados para que sus miembros mantengan acceso al historial. | OBS | [VolunteerService][groups] `getUserSearchGroups`; — |
| RF-GRU-011 | Listar grupos por mascota | Dueño/admin ve los de la mascota; otros usuarios solo sus membresías activas. | Filtra por petId y permiso; esta ruta no incluye a usuarios externos solo porque un grupo sea público. | OBS | [SearchGroupController][groups-api] `listForPet`; — |
| RF-GRU-015 | Consultar miembros activos | Dueño de mascota/admin consulta miembros. | Solo devuelve activos; creador que no sea dueño/admin no cumple la regla actual de este endpoint. Acordar D04. | OBS | [SearchGroupController][groups-api] `volunteers`, [VolunteerRepository][volunteers-repo]; — |
| RF-GRU-003 | Salir voluntariamente | Voluntario desactiva su membresía; el creador permanece activo. | Se conserva registro con active=false y revoked=false. En público activo puede volver por join; en privado necesita que dueño/creador/admin lo agregue. | OBS | [VolunteerService][groups] `leaveSearchGroup`; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-004 | Expulsar a un miembro | Dueño/creador/admin desactiva al miembro; no permite expulsar al creador. | La expulsión marca revoked=true y bloquea la reactivación directa. Si intenta volver, crea una solicitud pendiente que solo el creador puede aprobar o rechazar. | OBS | [VolunteerService][groups] `removeVolunteer`, [VolunteerRejoinRequest][rejoin-entity]; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-005 | Reincorporar a un antiguo miembro | Aplicar reingreso por visibilidad, causa de baja y estado del grupo. | Una salida voluntaria sigue RF-GRU-003; una expulsión crea solicitud y notifica al creador; solo este puede resolverla. Un grupo cerrado no permite aprobar altas. | OBS | [VolunteerService][groups] `joinSearchGroupWithResult/approveRejoinRequest/rejectRejoinRequest`; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-007 | Editar grupo y cambiar estado | Dueño/creador/admin con permisos de gestión cambia datos del grupo; solo el creador cambia estado. | Nombre vacío y cobertura negativa se rechazan. El estado admite `ACTIVE` o `CLOSED`; un grupo cerrado no acepta joins, altas ni mensajes y sale del descubrimiento. Solo el creador puede cerrarlo o reabrirlo; miembros autorizados conservan lectura del historial. | OBS | [VolunteerService][groups] `updateSearchGroup/closeSearchGroup`; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-GRU-013 | Gestionar imagen del grupo | Gestor sube imagen y usuarios con acceso la consultan. | Admite JPEG/PNG/WebP/GIF, aplica límite; imagen o grupo no accesible devuelve 404. | OBS | [VolunteerService][groups] `setImageIfPresent/findImageById`, [SearchGroupController][groups-api]; — |
| RF-GRU-014 | Asociar enlace de WhatsApp | Gestor configura o elimina enlace del grupo. | Solo HTTPS en chat.whatsapp.com, www.whatsapp.com o wa.me; cadena vacía elimina el enlace. | OBS | [VolunteerService][groups] `normalizeWhatsappGroupLink`; — |

### Chat de búsqueda

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-CHA-001 | Leer mensajes del grupo | Miembro activo, dueño/creador o admin lee chat. | Usuario autenticado ajeno no entra solo por ser grupo PUBLIC; mensajes ascendentes, defecto 50 y máximo 200. | OBS | [SearchGroupChatService][group-chat] `listMessages/requireChatAccess`; — |
| RF-CHA-002 | Enviar mensaje al grupo | Mismos permisos que lectura. | Recorta espacios; rechaza vacío y más de 2000 caracteres; guarda remitente, grupo y fecha, devuelve 201. | OBS | [SearchGroupChatService][group-chat] `addMessage`; — |

### Catálogo y administración de paseadores

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-PAS-001 | Solicitar alta como paseador | Visitante crea cuenta WALKER y perfil pendiente. | Correo único y contraseña no vacía; perfil PENDING no aparece en catálogo público. | OBS | [WalkerMarketplaceService][market] `createWalkerApplication`; — |
| RF-PAS-002 | Consultar catálogo público | Visitante lista paseadores APPROVED y activos. | Excluye pendientes, rechazados e inactivos; paginación defecto 50, máximo 200. El DTO incluye teléfono y correo, ver D04. | OBS | [WalkerMarketplaceService][market] `listPublicWalkers/toWalkerCard`; — |
| RF-PAS-003 | Consultar perfil propio | Usuario autenticado consulta su perfil de paseador, si existe. | Devuelve perfil asociado a su ID o resultado nulo si no existe. | OBS | [WalkerMarketplaceService][market] `getMyWalkerProfile`; — |
| RF-PAS-004 | Listar paseadores para administración | Administrador consulta todos los perfiles. | Incluye todos los estados de aprobación y actividad, con paginación. | OBS | [WalkerMarketplaceService][market] `listAllWalkers`, [SecurityConfig][security]; — |
| RF-PAS-005 | Crear paseador administrativamente | Administrador crea usuario y perfil. | Por defecto APPROVED y activo; valida identidad nueva y contraseña; admite estado explícito. | OBS | [WalkerMarketplaceService][market] `createWalkerByAdmin`; — |
| RF-PAS-006 | Editar y aprobar paseador | Administrador actualiza perfil, contacto, contraseña opcional, aprobación y actividad. | Cambiar a REJECTED o inactive lo retira del catálogo; definir efecto sobre solicitudes y paseos existentes. | OBS | [WalkerMarketplaceService][market] `updateWalkerByAdmin`; D05; — |
| RF-PAS-007 | Eliminar paseador | Administrador elimina perfil y usuario relacionado. | Debe acordarse qué sucede si hay solicitudes, mensajes o paseos asociados antes de garantizar eliminación completa. | PAR | [WalkerMarketplaceService][market] `deleteWalkerByAdmin`, [esquema SQL][schema]; D12; — |

### Solicitudes y negociación de paseos

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-SOL-001 | Solicitar paseo | Usuario autenticado elige paseador aprobado/activo, mascota propia opcional, fecha, duración, presupuesto y dirección. | Crea PENDING; mascota ajena o paseador no disponible se rechaza; notas iniciales crean mensaje. | OBS | [WalkerMarketplaceService][market] `createWalkRequest`; — |
| RF-SOL-002 | Listar solicitudes de mi rol | Dueño ve propias; WALKER ve asignadas; admin ve todas. | Solo devuelve solicitudes del ámbito aplicable, paginadas hasta 200. Definir cuenta que actúa como dueño y paseador. | OBS | [WalkerMarketplaceService][market] `listMyWalkRequests`; D05; — |
| RF-SOL-003 | Consultar detalle de solicitud | Dueño, paseador asignado o administrador. | Devuelve detalle y paseo asociado si existe; tercero recibe rechazo. | OBS | [WalkerMarketplaceService][market] `getWalkRequest/assertRequestAccess`; — |
| RF-SOL-004 | Cotizar paseo | Paseador asignado/admin fija precio y estado de negociación. | Sin estado usa NEGOTIATING; definir transiciones válidas, pues hoy acepta cualquier valor del enum del DTO. | PAR | [WalkerMarketplaceService][market] `quoteWalkRequest`; D05; — |
| RF-SOL-005 | Decidir sobre solicitud | Dueño/admin acepta, rechaza o cancela. | Solo admite ACCEPTED, REJECTED o CANCELLED; falta restringir desde qué estados y conciliar un paseo ya iniciado. | PAR | [WalkerMarketplaceService][market] `decideWalkRequest`; D05; — |
| RF-SOL-006 | Consultar conversación de solicitud | Dueño, paseador asignado o admin consulta mensajes. | Orden ascendente, paginación; tercero no accede. | OBS | [WalkerMarketplaceService][market] `listMessages`; — |
| RF-SOL-007 | Enviar mensaje de negociación | Participantes o admin envían texto no vacío. | Guarda remitente/fecha y actualiza la solicitud; falta validar longitud máxima de 2000 antes de persistir. | PAR | [WalkerMarketplaceService][market] `addMessage`, [WalkMessage][walk-message]; RNF-SEG-008; — |

### Ejecución de paseos

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-PSE-001 | Iniciar paseo | Paseador asignado/admin inicia solicitud ACCEPTED o NEGOTIATING. | Crea/reutiliza paseo, fija precio cotizado o presupuesto e inicio; ambos registros quedan IN_PROGRESS. Decidir si NEGOTIATING debe permitirse. | DEC | [WalkerMarketplaceService][market] `startWalk`; D05; — |
| RF-PSE-002 | Listar paseos | Dueño, paseador o admin consulta paseos de su ámbito. | Lista paginada por inicio con estados activos e históricos. | OBS | [WalkerMarketplaceService][market] `listMyDogWalks`; — |
| RF-PSE-003 | Consultar paseo | Solo participantes o administrador. | Devuelve estado, precio, fechas y datos asociados; tercero es rechazado. | OBS | [WalkerMarketplaceService][market] `getDogWalk/assertDogWalkAccess`; — |
| RF-PSE-004 | Registrar posición del paseo | Paseador asignado/admin reporta posición de paseo IN_PROGRESS. | Rechaza paseo terminado y dueño que intenta enviar posiciones; fecha de registro se asigna en servidor. | OBS | [WalkerMarketplaceService][market] `addWalkPosition`; — |
| RF-PSE-005 | Consultar recorrido del paseo | Participantes/admin consultan posiciones. | Orden por fecha, defecto 500 y máximo 2000 por página. | OBS | [WalkerMarketplaceService][market] `listWalkPositions`; — |
| RF-PSE-006 | Completar paseo | Paseador asignado/admin marca finalización. | Paseo y solicitud quedan COMPLETED con fecha final; definir estados de origen y repetición porque hoy no se comprueban. | PAR | [WalkerMarketplaceService][market] `completeWalk`; D05; — |

### Notificaciones

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-NOT-001 | Enviar correo | Sistema usa SMTP si está configurado. | Destino/asunto/cuerpo ausentes omiten envío; proveedor ausente o excepción registra fallo sin propagarlo al llamador. | OBS | [NotificationService][notify] `sendEmail`; — |
| RF-NOT-002 | Enviar SMS y WhatsApp | Sistema usa Twilio; WhatsApp requiere habilitación y remitente. | SMS se solicita y WhatsApp solo si está configurado; configuración ausente o excepción no derriba el flujo. | OBS | [NotificationService][notify], [TwilioSmsProvider][sms]; T06 parcial |
| RF-NOT-003 | Publicar alertas en tiempo real | Sistema publica texto en /topic/alerts/{ownerId}. | Usuario correcto recibe aviso; protección de suscripciones no está implementada en la configuración revisada. | PAR | [NotificationService][notify] `sendRealtimeAlert`, [WebSocketConfig][ws]; B09; — |
| RF-NOT-004 | Avisar al dueño de pérdida y recuperación | Eventos de mascota generan mensajes coherentes con su estado. | Nombre/estado corresponden a mascota y evento; eliminación de duplicados y textos fijos pendiente para pérdida. | PAR | [LostPetNotificationService][lost-notify]; T07 divergente; B06 |
| RF-NOT-005 | Avisar a voluntarios por geocerca | Sistema busca membresías activas de grupos de la mascota. | Inactivos no son destinatarios; envía por contactos disponibles. Decidir deduplicación entre grupos y condición LOST. | OBS | [VolunteerNotificationService][volunteer-notify], [VolunteerRepository][volunteers-repo]; D06; — |
| RF-NOT-006 | Notificar altas de miembros | Agregado avisa al voluntario; join nuevo/reactivado avisa al dueño; solicitud de reingreso avisa al creador y su resolución avisa al solicitante. | Join ya activo no notifica; solicitudes y decisiones notifican después del commit. Envío asíncrono no durable ante caída del proceso. | OBS | [VolunteerService][groups], [VolunteerNotificationService][volunteer-notify]; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RF-NOT-007 | Convocar colaboradores cercanos | Capacidad propuesta para seleccionar voluntarios por cercanía a una mascota perdida. | No está implementada una convocatoria geográfica independiente. Las notificaciones existentes se dirigen a miembros activos de los grupos correspondientes, sin selección por radio. Definir radio, ubicación y consentimiento antes de implementarla. | PEN | D06; — |

### Administración y panel web

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-ADM-001 | Consultar usuarios y planes | Admin lista ID, contacto, rol, plan y cantidad de mascotas. | Usuario no admin no accede; definir paginación del listado actualmente completo. | OBS | [UserPlanController][user-plans] `listUsers`; RNF-REN-002; — |
| RF-ADM-002 | Consultar métricas operativas | Admin ve HTTP, PostgreSQL, Hikari y colas GPS. | Incluye hora de captura, solicitudes, 4xx/5xx, transacciones, conexiones y descartes; contadores HTTP/GPS se reinician con proceso. | OBS | [AdminMetricsService][metrics], [HttpRequestMetricsService][http-metrics]; — |
| RF-WEB-001 | Navegar y conservar sesión | Visitante registra/inicia sesión; navegador conserva token y carga /api/me. | Recarga restaura sesión válida; fallo al cargar sesión ejecuta cierre local. | OBS | [app.js][ui] `bootstrap/loadSession`, [index.html][html]; — |
| RF-WEB-002 | Adaptar panel al rol | Dueño ve mascotas/solicitudes; paseador perfil/paseos; admin administración. | Controles visibles corresponden al rol; permisos efectivos siguen siendo responsabilidad del backend. | OBS | [app.js][ui] `renderDashboard`; — |
| RF-WEB-003 | Gestionar mascotas y compartir desde web | Dueño crea/lista mascotas y agrega persona autorizada. | Formularios consumen APIs y muestran errores de permisos/cuota; no se presume que toda operación REST tenga pantalla. | OBS | [app.js][ui] `createPet/sharePet`; — |
| RF-WEB-004 | Gestionar acceso temporal desde web | Dueño selecciona fechas, recurrencia y permisos; lista/revoca concesiones. | BASIC deshabilita creación; recurrente disponible según permisos de backend. UI restringe también a admin no PREMIUM: inconsistencia B14. | PAR | [app.js][ui] `createTemporaryAccess/updateTemporaryAccessFormState`; — |
| RF-WEB-005 | Mostrar comunidad y alertas | Usuario ve grupos, crea grupo, se une y consulta/cierra alertas según permisos. | Join informa si ya era miembro o fue reactivado y refresca grupos. | OBS | [app.js][ui] `createSearchGroup/joinSearchGroup/renderAlerts`; — |
| RF-WEB-006 | Negociar paseos desde web | Usuario selecciona paseador, crea solicitud, conversa y decide; paseador cotiza/inicia. | Acciones consumen endpoints correspondientes y actualizan solicitud/chat. | OBS | [app.js][ui] `submitWalkRequest/renderRequestActions/sendMessage`; — |
| RF-WEB-007 | Visualizar paseo y enviar posición | Participantes ven mapa Leaflet; paseador envía posición del navegador manualmente. | Mapa muestra marcadores y recorrido; permiso geográfico denegado informa error; botón de completar actualiza paseo. | OBS | [app.js][ui] `renderDogWalkMap/sendCurrentPosition/completeSelectedWalk`; — |
| RF-WEB-008 | Consultar distancias y descargar informes | Usuario ve distancias semanales y controles de exportación. | Muestra kilómetros por día y descarga CSV/PDF cuando API autoriza; no existe comparación mensual implementada. | PAR | [app.js][ui] `loadPetWalkDistances/exportPetHistory`; RF-PLA-007; — |
| RF-WEB-009 | Administrar paseadores y ver métricas desde web | Admin crea, modifica, aprueba, desactiva o elimina perfiles. | Interfaz refleja operaciones administrativas y muestra métricas recuperadas del backend. | OBS | [app.js][ui] `handleAdminWalkerAction/renderAdminMetrics`; — |
| RF-WEB-010 | Refrescar datos de sesión | Navegador consulta solicitudes, conversación, mapa y alertas cada 15 segundos. | Sin token no ejecuta ese refresco; actualización es polling, no streaming de todas las ubicaciones. | OBS | [app.js][ui] `setInterval`; — |
| RF-WEB-011 | Elegir tema visual | Usuario alterna FiruApp/clásico. | Preferencia queda en localStorage y se aplica al recargar. | OBS | [app.js][ui], [app.css][css]; — |

### Datos de demostración

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-DEM-001 | Preparar cuentas demo de paseadores | Sistema puede crear cuentas/perfiles de demostración al arrancar. | Repetición no duplica cuentas/perfiles; DEMO_WALKERS_ENABLED=false omite creación. Definir desactivación de producción. | OBS | [DemoWalkerDataService][demo-walkers], [application.yml][config]; RNF-SEG-010; — |
| RF-DEM-002 | Preparar mascotas demo al iniciar sesión | Solo cuenta demo configurada con demo GPS habilitado recibe datos sintéticos. | Credenciales válidas preceden al sembrado; no modifica mascotas reales ni de otra cuenta y evita duplicar rutas recientes. | OBS | [LoginDemoDataService][demo-login], [DemoAccount][demo-account]; T08, T09 |
| RF-DEM-003 | Simular posiciones y salida de zona | Tarea opcional mueve solo mascotas sintéticas de la cuenta demo. | Usa fechas de app; excluye mascotas ligadas a terminal real; salida genera alerta interna sin SMS/correo real. | OBS | [DemoGpsSimulator][demo-gps]; T10 |

### Transporte

Transit se ejecuta desde `../transit` como otra aplicación Spring Boot. Su README informa que la configuración de autenticación de FiruApp no se trasladó; por tanto, este catálogo no atribuye una garantía ADMIN a sus rutas. Los requisitos RF-TRA son inventario funcional que falta validar con el proyecto independiente.

| ID | Nombre | Actor y regla | Criterio de aceptación | Estado | Código y pruebas |
| --- | --- | --- | --- | --- | --- |
| RF-TRA-001 | Registrar vehículo | Admin registra TAXI/BUS, placa, ruta, operador, estado y dueño. | Valores ausentes usan TAXI/ACTIVE; ownerId solo resuelve usuario existente. | OBS | [TransitVehicleController][transit-api], [TransitVehicleService][vehicles]; — |
| RF-TRA-002 | Listar vehículos | Admin consulta flota. | Lista paginada, defecto 50 y máximo 100. | OBS | [TransitVehicleController][transit-api] `list`; — |
| RF-TRA-003 | Consultar vehículo | Admin consulta por ID. | Vehículo inexistente devuelve 404; respuesta incluye tipo, estado y propietario. | OBS | [TransitVehicleController][transit-api] `getById`; — |
| RF-TRA-004 | Registrar posición vehicular | Admin envía coordenadas, fuente y fecha opcional. | Persiste y solicita auditoría de geocercas; fecha ausente usa hora del servidor, cuya normalización queda pendiente. | OBS | [TransitLocationService][transit-location], [TransitLocationIngestionService][transit-ingestion]; RNF-DAT-004; — |
| RF-TRA-005 | Consultar última posición vehicular | Admin consulta última ubicación de vehículo. | Sin posición responde 404; con datos devuelve la más reciente por timestamp. | OBS | [TransitLocationService][transit-location]; — |
| RF-TRA-006 | Consultar ruta vehicular | Admin solicita últimas horas, defecto 3 y mínimo 1. | Devuelve posiciones ascendentes dentro del período; falta límite de volumen/rango. | PAR | [TransitVehicleController][transit-api] `route`, [TransitLocationService][transit-location]; RNF-REN-003; — |
| RF-TRA-007 | Generar reporte vehicular | Admin solicita DAY o WEEK. | Reporta puntos, distancia, salidas HOME/WORK, grupos activos y última fecha; otro window usa un día y requiere aclarar contrato. | OBS | [TransitReportService][transit-reports]; D11; — |
| RF-TRA-008 | Crear geocerca programada | Admin define HOME/WORK, CIRCLE/POLYGON, días, horas y enabled. | Se evalúa únicamente en días/horas activos; horario puede cruzar medianoche. Validación geométrica completa pendiente. | OBS | [TransitGeofencingService][transit-geofence] `createGeofence`; RNF-SEG-008; — |
| RF-TRA-009 | Listar geocercas vehiculares | Admin consulta las de un vehículo. | Lista paginada hasta 100. | OBS | [TransitGeofenceController][transit-geofence-api]; — |
| RF-TRA-010 | Eliminar geocerca vehicular | Admin elimina por ID. | Inexistente responde 404; revisar eventos dependientes al borrar. | PAR | [TransitGeofenceController][transit-geofence-api], [TransitGeofencingService][transit-geofence]; D12; — |
| RF-TRA-011 | Detectar salida de zona programada | Sistema evalúa cada zona habilitada aplicable al timestamp de posición. | Salida crea evento y notifica dueño y participantes de grupos activos, deduplicados por usuario entre grupos. | OBS | [TransitGeofencingService][transit-geofence] `auditLocation/notifyStakeholders`; — |
| RF-TRA-012 | Crear grupo de búsqueda vehicular | Admin crea grupo por incidente con creador y miembros iniciales. | Guarda datos, incorpora creador y notifica a usuarios añadidos. | OBS | [TransitSearchGroupService][transit-groups] `create`; — |
| RF-TRA-013 | Listar grupos vehiculares | Admin consulta grupos de vehículo o grupos donde es voluntario activo. | Ambas consultas aplican paginación; la lista personal excluye membresías inactivas. | OBS | [TransitSearchGroupController][transit-groups-api], [TransitSearchGroupService][transit-groups]; — |
| RF-TRA-014 | Unirse a grupo vehicular | Admin autenticado crea/reactiva su membresía. | Miembro activo no se duplica secuencialmente; reactivación restaura relación; revisar concurrencia en RNF-DAT-001. | OBS | [TransitSearchGroupService][transit-groups] `joinSearchGroup`; — |
| RF-TRA-015 | Salir de grupo vehicular | Admin voluntario desactiva su membresía. | Conserva fila inactiva y elimina usuario del conjunto de miembros del grupo. | OBS | [TransitSearchGroupService][transit-groups] `leaveSearchGroup`; — |

### Funcionalidades previstas o expresamente fuera del alcance actual

Estas capacidades figuran como ausentes, preguntas abiertas o evolución futura en el README y el documento de diseño. Su inclusión permite rastrearlas; no implica compromiso de entrega ni aprobación de sus reglas. Sus criterios deberán detallarse antes de implementarlas.

| ID | Nombre | Actor y regla | Criterio de aceptación inicial | Estado | Fuente |
| --- | --- | --- | --- | --- | --- |
| RF-FUT-001 | Cobrar servicios y gestionar pagos | Dueño, paseador y plataforma; pagos o custodia de fondos aún sin reglas. | Acordar proveedor, estados, confirmación, devolución y conciliación antes de habilitar cobros reales. | PEN | [README][readme], [Diseño][design] |
| RF-FUT-002 | Calificar paseadores | Participantes califican servicios según política por definir. | Definir quién puede calificar, qué paseo lo habilita y cómo se modera una reseña. | PEN | [README][readme], [Diseño][design] |
| RF-FUT-003 | Gestionar disponibilidad horaria | Paseador publica franjas y dueño solicita disponibilidad. | Definir zona horaria, solapamientos, reservas y actualización de cupos. | PEN | [README][readme] |
| RF-FUT-004 | Gestionar incidencias y disputas | Participantes y soporte tramitan problemas y cancelaciones. | Definir evidencia, responsables, estados, resolución y eventuales cargos de cancelación. | PEN | [README][readme], [Diseño][design] |
| RF-FUT-005 | Ofrecer aplicación móvil o PWA | Usuarios acceden desde un cliente dedicado. | Acordar plataformas, instalación y capacidades sin conexión; el panel adaptable actual no acredita una PWA. | PEN | [README][readme], [Diseño][design] |
| RF-FUT-006 | Actualizar mapas por streaming | Participantes reciben nuevas posiciones sin consultar periódicamente. | Acordar WebSocket/SSE, autorización, reconexión y actualización de posiciones; el topic de alertas actual no cubre este flujo. | PEN | [Diseño][design] |
| RF-FUT-007 | Separar organizaciones | Usuarios y recursos pueden pertenecer a organizaciones aisladas. | Definir organización, administración y límites; demostrar aislamiento antes de ofrecer multitenencia. | PEN | [README][readme], [Diseño][design] |
| RF-FUT-008 | Integrar proveedores de identidad | Usuario inicia sesión mediante un proveedor externo. | Definir proveedor, vinculación de cuentas y tratamiento de roles; JWT propio no equivale a esta integración. | PEN | [README][readme], [Diseño][design] |

### Compromisos comerciales y operativos

Se registran como oferta documentada en el repositorio, no como precios verificados externamente ni como funciones ya automatizadas.

| ID | Nombre | Actor y regla | Criterio de aceptación inicial | Estado | Fuente |
| --- | --- | --- | --- | --- | --- |
| RF-COM-001 | Ofrecer paquetes de collar y servicio | Oferta BASIC: COP 319.000 y 3 meses gratis; FAMILY: COP 379.000 y 3 gratis + 3 pagos; PREMIUM: COP 469.000 y 3 gratis + 9 pagos. | Acordar vigencia, activación, vencimiento y renovación; no hay facturación ni caducidad de suscripción identificada en el backend. | EXT | [Planes y límites][commercial]; D02 |
| RF-COM-002 | Prestar soporte y configuración | Todos los planes incluyen soporte WhatsApp; PREMIUM añade una sesión personal de 15 minutos. | Definir canal, horario, responsable y constancia de atención/sesión. | EXT | [Planes y límites][commercial] |
| RF-COM-003 | Atender garantía del dispositivo | Oferta incluye un año de garantía del equipo. | Definir inicio, condiciones y proceso de reclamación; su cumplimiento requiere evidencia operativa externa. | EXT | [Planes y límites][commercial] |

## Requisitos no funcionales

Las configuraciones actuales son evidencia de implementación, no resultados de rendimiento ni garantías de servicio. Los objetivos sin valor acordado se indican como PROP y deben concretarse en D09 antes de poder aprobarse.

### Seguridad y privacidad

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-SEG-001 | Autenticación y roles en HTTP | Toda ruta no declarada pública exige JWT; administración y transporte exigen ADMIN. | Matriz de pruebas con visitante, USER, WALKER y ADMIN rechaza acceso indebido a cada operación. Las rutas públicas incluyen aceptación temporal por token. | OBS | [SecurityConfig][security], [JwtAuthFilter][jwt-filter]; [Matriz API](matriz-trazabilidad-api.md) |
| RNF-SEG-002 | Autorización por recurso y permiso | El rol se complementa con propiedad, relación activa y permiso del dato consultado. | Probar IDs ajenos, membresías inactivas, permisos temporales independientes y revocación inmediata. Brechas B01, B03 y B04. | PAR | [PetService][pets], [TemporaryPetAccessService][temporary], [VolunteerService][groups], [SearchGroupChatService][group-chat] |
| RNF-SEG-003 | Proteger credenciales almacenadas | Contraseñas con BCrypt; invitaciones temporales con token aleatorio y hash SHA-256 almacenado. | La BD conserva hashes de contraseña/token; los DTO de respuesta no exponen contraseña original ni token de invitación. Token aceptado deja de servir. RF-TMP-002 envía credenciales iniciales por correo. | OBS | [SecurityConfig][security], [AuthController][auth], [TemporaryPetAccessService][temporary] |
| RNF-SEG-004 | Gestionar secretos de despliegue | Credenciales se suministran por entorno; producción no utiliza el secreto JWT de desarrollo. | Railway aborta si falta un secreto distinto del predeterminado. Extender la comprobación a otros entornos productivos y definir rotación. | PAR | [application.yml][config], [RailwayDeploymentValidator][railway-validator] |
| RNF-SEG-005 | Restringir orígenes permitidos | Orígenes HTTP y WebSocket corresponden a clientes autorizados. | Origen no incluido falla en la política del navegador; comprobar también controles del servidor. HTTP usa lista configurable; WebSocket admite *. | PAR | [SecurityConfig][security], [WebSocketConfig][ws] |
| RNF-SEG-006 | Autorizar suscripciones de alertas | Cada conexión solo recibe topics de usuarios o recursos autorizados. | Cliente anónimo o cuenta ajena no puede suscribirse a /topic/alerts/{ownerId}. No se identificó interceptor de autorización STOMP. | PAR | [WebSocketConfig][ws], [NotificationService][notify]; B09 |
| RNF-SEG-007 | Minimizar exposición de datos personales | Listados públicos y respuestas compartidas muestran solo datos aprobados para esa audiencia. | Revisar campos por actor: grupos públicos ocultan miembros/cuenta, pero exponen contacto del líder; catálogo de paseadores incluye correo/teléfono. Acordar consentimiento en D04. | PAR | [SearchGroupMapper][group-map], [WalkerMarketplaceService][market], [PetMapper][pet-map] |
| RNF-SEG-008 | Validar entradas antes de persistir | Campos requeridos, longitudes, coordenadas, geometrías, montos, duraciones y rangos deben validarse de forma consistente. | Casos nulos, texto excesivo, latitud/longitud fuera de rango, polígonos inválidos y cantidades negativas devuelven error de cliente sin escrituras parciales. La validación actual es desigual. | PAR | DTO/controladores, [GeofencingService][geofence], [WalkerMarketplaceService][market], [ApiExceptionHandler][errors]; B11 |
| RNF-SEG-009 | Generar exportaciones seguras | Texto aportado por usuarios no debe ejecutarse como fórmula al abrir CSV. | Probar valores que comienzan con =, +, - y @ y separadores/saltos de línea; acordar neutralización compatible con las hojas de cálculo admitidas. Escapar comillas no cubre por sí solo las fórmulas. | PAR | [PetService][pets] `exportHistory/csv`; RF-LOC-007 |
| RNF-SEG-010 | Aislar demostraciones de producción | Cuentas, posiciones y mensajes sintéticos requieren habilitación deliberada por entorno. | Producción no crea cuentas demo ni envía datos sintéticos salvo configuración aprobada; probar arranque y login. Demo GPS está apagado por defecto, pero demo walkers está encendido. | PAR | [application.yml][config], [DemoWalkerDataService][demo-walkers], [DemoGpsSimulator][demo-gps]; T08–T10 parciales |
| RNF-SEG-011 | Definir revocación y recuperación de sesión | Política pendiente para JWT comprometido, cierre remoto, cambio de contraseña y recuperación de cuenta. | Acordar mecanismos y plazo de invalidación; hoy logout solo elimina sesión local y JWT vence según configuración, por defecto 24 horas. | PROP | [JwtProvider][jwt], [app.js][ui]; RF-AUT-005 |
| RNF-SEG-012 | Limitar abuso de interfaces | Establecer cuotas de intentos y solicitudes para login, registro, invitaciones y entradas públicas. | Definir límites por identidad/origen, respuesta al exceso y recuperación; no se identificó limitador en la aplicación. | PROP | [SecurityConfig][security], controladores públicos |
| RNF-SEG-013 | Proteger comunicaciones y acceso de dispositivos | Definir HTTPS/WSS y protección compatible con los protocolos GPS admitidos. | Verificar certificados, transporte externo y autenticidad de terminales en despliegue; conocer un terminalId no debe confundirse con prueba criptográfica de identidad. | PROP | [GpsTcpServer][gps-server], [GpsMessageHandler][gps-handler], [TopinSessionHandler][topin-session]; D07 |
| RNF-SEG-014 | Comprobar contenido de imágenes | Tamaño y formato aceptado deben verificarse antes de publicar un archivo. | Exceso produce 413; comprobar que bytes corresponden al formato declarado y rechazar contenido incompatible. Hoy se comprueban tamaño y MIME declarado, sin decodificación integral de imagen. | PAR | [ImageUploadLimits][images], [PetService][pets], [VolunteerService][groups]; T01–T03 parciales |

### Rendimiento y capacidad

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-REN-001 | Medir latencia de operaciones | Acordar tiempos de respuesta por consulta, escritura, exportación e ingestión. | Definir p95/p99, volumen de datos, concurrencia, entorno y duración; medir errores además de latencia. No hay umbrales aprobados identificados. | PROP | D09; [Diseño][design] |
| RNF-REN-002 | Paginar sin cargar listas completas | Listados voluminosos limitan datos desde la base de datos y permiten continuar la consulta. | Página solicitada solo carga su volumen necesario; probar usuarios, alertas, mascotas y grupos. Algunas rutas paginan; mascotas filtra/corta en memoria y otros listados son completos. | PAR | [PetService][pets], [UserPlanController][user-plans], [AlertService][alerts], [VolunteerService][groups]; B10 |
| RNF-REN-003 | Acotar rutas y exportaciones | Rangos y tamaños deben tener límite documentado o procesamiento por lotes. | Solicitud grande conserva memoria y tiempo dentro del presupuesto acordado. Mascotas limita rutas a 720 puntos; exportaciones y rutas/reportes de transporte cargan el rango completo. | PAR | [LocationService][locations], [PetService][pets], [TransitLocationService][transit-location], [TransitReportService][transit-reports] |
| RNF-REN-004 | Evitar trabajo bloqueante en Netty | El hilo de red no debe esperar persistencia ni servicios externos. | La caché se precarga al iniciar; JT808 espera la admisión fuera del event loop; Topin usa miss cerrado mientras el executor limitado carga la entrada. Probar BD lenta y executor saturado: otros terminales no se bloquean; el primer login Topin sin caché puede requerir reconexión. | PAR | [GpsMessageHandler][gps-handler], [GpsIngestionService][ingestion] `isKnownTerminalIdAsync`, [TopinSessionHandler][topin-session]; B18 |
| RNF-REN-005 | Acotar y configurar ejecutores | GPS, geocercas y notificaciones usan colas e hilos limitados con política de saturación. | Probar carga por encima de capacidad y comportamiento de rechazo; permitir ajuste por entorno. GPS es configurable; los demás tamaños están fijados en código. | PAR | [AlertConfig][executors], [GpsIngestionService][ingestion], [application.yml][config] |
| RNF-REN-006 | Controlar consultas y llamadas repetidas | Índices, proyecciones, cachés y cargas relacionadas deben evitar crecimiento innecesario por elemento. | Medir consultas SQL y llamadas al geocodificador al crecer una lista. Existen índices/EntityGraph/cachés, pero mapear mascotas puede consultar o resolver barrio por cada elemento. | PAR | [schema.sql][schema], repositorios, [PetMapper][pet-map], [NeighborhoodLookupService][neighborhood] |
| RNF-REN-007 | Limitar tramas incompletas y conexiones ociosas | Decodificadores deben acotar acumulación de bytes y tiempo de espera de un mensaje. | Enviar flujo sin delimitador final y comprobar memoria/límite de desconexión. V41 espera delimitador sin límite explícito; Topin comprueba longitudes de sus perfiles. | PAR | [V41FrameDecoder][v41-frame], [TopinFrameDecoder][topin-frame], [GpsTcpServer][gps-server] |
| RNF-REN-008 | Configurar recursos HTTP y BD | Hikari, Tomcat y compresión permiten ajustar capacidad al entorno. | Configuración efectiva refleja variables; defaults Hikari máximo 20/mínimo 5, espera 30 s; Tomcat máximo 200; compresión desde 1024 bytes. Estos valores no acreditan una carga soportada. | OBS | [application.yml][config] |
| RNF-REN-009 | Definir capacidad sostenida y de ráfaga | Acordar terminales simultáneos, posiciones/segundo, usuarios y volumen histórico. | Prueba de carga mide pérdida, retraso de cola, CPU, memoria y conexiones bajo el perfil de D09. | PROP | D09; [GpsIngestionService][ingestion], [AdminMetricsService][metrics] |

### Confiabilidad y continuidad

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-CON-001 | Definir disponibilidad del servicio | Acordar SLO por API, recepción GPS y alertas, incluyendo dependencias. | Establecer ventana de medición, tratamiento de mantenimiento y presupuesto de errores; medirlo en el entorno objetivo. | PROP | D09; [Diseño][design] |
| RNF-CON-002 | Definir garantía de entrega GPS | Recepción/ACK, encolado y persistencia son estados diferentes; debe acordarse qué pérdida es tolerable. | ACK positivo de ubicación espera commit o rechazo deliberado; error de BD/capacidad produce respuesta negativa o sin ACK. Reconciliar recibidos, persistidos, rechazados y fallidos. La cola opcional sigue en memoria y un cierre abrupto puede perder mensajes. | PAR | [GpsIngestionService][ingestion], [GpsMessageHandler][gps-handler], [TopinLocationHandler][topin-location], [GpsKafkaProducer][kafka-producer], [GpsKafkaConsumer][kafka-consumer]; D07 |
| RNF-CON-003 | Notificar después de confirmar cambios | Una notificación de membresía debe corresponder a una transacción confirmada y ejecutarse fuera de su trabajo de BD. | Los eventos de membresía y reingreso se atienden tras el commit; el envío sigue siendo en memoria y no tiene reintento durable. Falta comprobar rollback y proveedor lento. | PAR | [VolunteerService][groups], [VolunteerNotificationService][volunteer-notify]; B05 |
| RNF-CON-004 | Aislar fallos de procesos secundarios | SMTP, Twilio, geocodificación y evaluación de zonas tienen política explícita de fallo. | Fallo externo no pierde ubicación confirmada; informar qué se omitió y definir reintento/dead letter. Hay capturas de excepciones, pero no entrega garantizada ni reintentos persistentes. | PAR | [NotificationService][notify], [GeofencingService][geofence], [NeighborhoodLookupService][neighborhood], [GpsIngestionService][ingestion] |
| RNF-CON-005 | Respaldar y recuperar datos | Acordar frecuencia, almacenamiento, responsables, RPO y RTO de PostgreSQL y archivos/configuración necesarios. | Restaurar una copia en entorno aislado y medir pérdida/tiempo frente al objetivo; no se encontró procedimiento verificable de restauración. | PROP | D08, D09; [README][readme] |
| RNF-CON-006 | Terminar procesos sin pérdida indeterminada | Cierre controlado debe definir qué ocurre con conexiones, tareas y cola pendiente. | El listener deja de aceptar y cierra clientes/event loops primero; executor GPS y consumidores esperan hasta 30 s configurables y cuentan ubicaciones en cola abandonadas al vencer. La terminación abrupta aún puede perder trabajo en memoria. | PAR | [GpsTcpServer][gps-server], [GpsIngestionService][ingestion], [AlertConfig][executors]; B16 |

### Integridad y ciclo de vida de datos

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-DAT-001 | Hacer única y consistente la membresía | Volunteer es la fuente de verdad y debe existir una fila por usuario/grupo; solo puede existir una solicitud PENDING por membresía. | El servicio bloquea el grupo durante cambios de membresía y la base impone unicidad; el esquema consolida filas duplicadas previas. Falta comprobar carreras con PostgreSQL real. | PAR | [VolunteerService][groups], [Volunteer][volunteer-entity], [VolunteerRejoinRequest][rejoin-entity], [schema.sql][schema]; T04 parcial; pruebas de servicio ejecutadas 15/15 |
| RNF-DAT-002 | Aplicar cuotas de forma atómica | Dos altas simultáneas no deben superar límite de personas, grupos o zonas. | Con una plaza restante, dos solicitudes concurrentes admiten solo una y la otra recibe respuesta controlada. Los conteos y guardados actuales no bastan para garantizarlo. | PAR | [PetAccessService][shares], [VolunteerService][groups], [GeofencingService][geofence] |
| RNF-DAT-003 | Respetar transiciones y operaciones repetidas | Solicitud, paseo y membresía deben mantener estados compatibles y una política de repetición. | Probar transiciones permitidas/prohibidas y repetición según D05; no reabrir ni finalizar procesos indebidamente. | PAR | [WalkerMarketplaceService][market], [VolunteerService][groups]; B07 |
| RNF-DAT-004 | Aplicar zona horaria coherente | Fechas de GPS, API, accesos, paseos y transporte siguen una convención explícita. | Pruebas incluyen UTC, zona del terminal, cambio de día y servidor en otra zona. Mascotas usa AppTimeService; transporte contiene usos de hora/zona del sistema. | PAR | [AppTimeService][time], [Jt808TimeZones][jt-time], servicios de transporte; T05, T13, T14 |
| RNF-DAT-005 | Definir retención y reducción de historial | Ventana comercial de consulta se distingue de conservación física, anonimización y reducción de puntos. | Acordar plazos y excepciones; verificar eliminación/reducción programada y efectos en informes/copias. No se identificó proceso operativo de retención. | PEN | [Diseño][design], [PlanPolicy][plans]; D08 |
| RNF-DAT-006 | Eliminar recursos con dependencias de forma definida | Borrado respeta relaciones y registros que deban conservarse. | Crear mascota/paseador/zona con dependencias y comprobar borrado o rechazo de negocio acordado; no depender de un error de clave foránea. | PAR | [PetService][pets], [WalkerMarketplaceService][market], [TransitGeofencingService][transit-geofence], [schema.sql][schema]; D12 |
| RNF-DAT-007 | Versionar cambios de esquema | Migraciones deben ser ordenadas, auditables y comprobables desde BD nueva y existente. | Arranque/migración detecta errores y conserva datos; ejecutar migraciones en integración. Hoy hay bootstrap SQL y validate de JPA, con continue-on-error=true por defecto. | PEN | [README][readme] roadmap, [schema.sql][schema], [application.yml][config] |

### Observabilidad

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-OBS-001 | Exponer estado operativo con acceso restringido | Métricas HTTP, PostgreSQL, Hikari y GPS solo para administradores. | Confirmar captura, autorización y significado de contadores; distinguir métricas de proceso de métricas acumuladas de BD. | OBS | [AdminMetricsService][metrics], [HttpRequestMetricsService][http-metrics]; RF-ADM-002 |
| RNF-OBS-002 | Hacer visibles saturación y descartes GPS | API de métricas expone recibidos, persistidos, rechazados, fallidos, rechazos del executor y drops de la cola; los contadores son de proceso. | Saturación controlada incrementa el contador correcto; comparar mensajes recibidos con resultados y distinguir fallos de rechazos deliberados. | PAR | [GpsIngestionService][ingestion], [AdminMetricsService][metrics], [app.js][ui]; B16 |
| RNF-OBS-003 | Mantener métricas históricas y alertas operativas | Recopilación externa permite tendencias, umbrales y detección de incidentes. | Definir métricas, retención, alertas y responsables; integrar Actuator/Micrometer/Prometheus u otra solución acordada. No están habilitados en la app revisada. | PEN | [Diseño][design], [build.gradle][build] |
| RNF-OBS-004 | Registrar fallos sin divulgar datos sensibles | Logs útiles para correlación deben minimizar contactos, coordenadas, tokens y cuerpos de mensajes. | Inspeccionar logs de éxito/error y redacción según política; hoy algunos mensajes incluyen contacto y contenido de notificación. | PAR | [NotificationService][notify], [TwilioSmsProvider][sms], [GpsIngestionService][ingestion]; D08 |
| RNF-OBS-005 | Controlar transacciones ociosas | Watchdog opcional solo actúa sobre sesiones de la misma BD, usuario y ApplicationName. | Deshabilitado no actúa; habilitado omite su propia sesión y registra cada terminación. Defaults: chequeo 30 s y antigüedad transaccional 1 min. | OBS | [JdbcConnectionWatchdog][watchdog], [application.yml][config] |

### Despliegue e interoperabilidad

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-OPE-001 | Construir un artefacto reproducible | Gradle wrapper, Java 21 y Docker definen compilación y empaquetado. | Entorno limpio compila/prueba y genera bootJar arrancable con dependencias declaradas; no se ejecutó esa verificación en esta revisión. | OBS | [build.gradle][build], [Dockerfile][docker], [Gradle wrapper][gradle-wrapper] |
| RNF-OPE-002 | Parametrizar despliegue y conectividad | BD, correo, Twilio, JWT, zonas horarias y capacidades usan configuración externa. | Variables Railway/PG se resuelven correctamente; HTTP y TCP GPS resultan alcanzables por clientes previstos. Configuración del repositorio no demuestra apertura de puertos externa. | OBS | [application.yml][config], [RailwayDatabaseEnvironmentPostProcessor][railway-env], [railway.json][railway] |
| RNF-OPE-003 | Definir salud y preparación | Despliegue debe distinguir proceso vivo de API/BD/listener preparados. | Acordar probes, dependencias, tiempos y reinicio; no se identificaron endpoints dedicados de liveness/readiness en la app. | PROP | [GpsTcpServer][gps-server], [railway.json][railway], [build.gradle][build] |
| RNF-OPE-004 | Permitir separar recepción GPS | Evolución prevista permite escalar recepción/procesamiento independientemente de HTTP. | Definir contrato de eventos, entrega y operación separada antes de dividir servicios; Kafka actual es una base incompleta y no acredita esa arquitectura funcionando. | PEN | [Diseño][design], [GpsKafkaProducer][kafka-producer], [GpsKafkaConsumer][kafka-consumer] |
| RNF-OPE-005 | Gestionar límites de proveedores | Integraciones deben tener timeout, tratamiento de error y uso compatible con su servicio. | Simular proveedor lento/no disponible; comprobar tiempo límite, caché y estado que recibe el usuario. Hay manejo parcial; falta una política uniforme comprobada. | PAR | [NeighborhoodLookupService][neighborhood], [NotificationService][notify], [TwilioSmsProvider][sms] |
| RNF-OPE-006 | Definir coherencia entre instancias | Roles/planes, cachés, tareas y suscripciones no pueden depender de una única memoria si se despliegan réplicas. | Probar cambios y eventos entre dos instancias; definir invalidación y coordinación. Caffeine, broker simple y colas actuales son locales. | PROP | [AppCacheConfig][app-cache], [GpsCacheConfig][gps-cache], [WebSocketConfig][ws], [GpsIngestionService][ingestion]; D10 |

### Usabilidad y compatibilidad de salida

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-USA-001 | Mostrar errores comprensibles y consistentes | API/UI deben distinguir permisos, validación, ausencia y fallo interno. | Casos inválidos devuelven código y mensaje útil sin stack trace; interfaz los presenta en español acordado. Handler y mensajes existen, pero campos inválidos y formatos no son uniformes. | PAR | [ApiExceptionHandler][errors], [app.js][ui]; B11 |
| RNF-USA-002 | Adaptar el panel a tamaños de pantalla | Formularios, listas y mapa deben seguir utilizables en los tamaños soportados. | Definir navegadores/anchos y comprobar navegación, lectura y controles sin pérdida de acciones. Hay reglas CSS adaptables; no se realizó prueba visual. | OBS | [app.css][css], [index.html][html] |
| RNF-USA-003 | Definir accesibilidad verificable | Interfaz debe tener objetivo explícito para teclado, foco, etiquetas, contraste y errores. | Acordar alcance y nivel; realizar comprobaciones automáticas y manuales con tecnologías de apoyo. No hay evaluación de conformidad en esta revisión. | PROP | [index.html][html], [app.js][ui], [app.css][css] |
| RNF-USA-004 | Preservar idioma y caracteres en exportación | Nombres, direcciones y mensajes deben conservarse en los formatos ofrecidos. | Exportar acentos, ñ y caracteres admitidos sin sustitución ni corrupción. El PDF actual reemplaza caracteres fuera de ASCII. | PAR | [PetService][pets] `buildPdf/pdfText`; RF-LOC-008; D11 |
| RNF-USA-005 | Explicar fechas, alcance y frescura | Mostrar zona horaria y última actualización cuando afectan la interpretación. | Usuario distingue hora de evento/recepción y datos históricos de actuales; acordar indicador de posición antigua y zona de formularios. | PROP | [app.js][ui], [AppTimeService][time]; D07, D11 |

### Mantenibilidad y verificación

| ID | Nombre | Regla o propiedad | Criterio de aceptación | Estado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| RNF-MAN-001 | Mantener responsabilidades separadas | Controladores exponen API, servicios aplican negocio, repositorios persisten y DTO delimitan respuestas. | Revisión de cada cambio identifica dónde vive la regla y evita devolver entidades con datos no previstos. La estructura existe; no implica ausencia de acoplamientos. | OBS | [Diseño][design], paquetes controller/service/repository/dto/mapper |
| RNF-MAN-002 | Verificar reglas críticas automáticamente | Autorización, concurrencia, planes, estados y protocolos requieren pruebas que ejerciten resultados observables. | Ejecutar pruebas unitarias e integración PostgreSQL/PostGIS; añadir casos de brechas antes de declararlas corregidas. Hay 18 clases de prueba, concentradas en GPS, imágenes y demo. | PAR | Inventario T01–T18; [build.gradle][build], [Diseño][design] roadmap |
| RNF-MAN-003 | Controlar contratos y compatibilidad | Cambios de API/DTO deben tener contrato y política de compatibilidad con clientes. | Acordar versionado y verificar ejemplos/errores, JSON y multipart; detectar cambios incompatibles en validación automática. | PROP | [Matriz API](matriz-trazabilidad-api.md), controladores/DTO |
| RNF-MAN-004 | Mantener trazabilidad de requisitos | Cada regla conserva ID, nombre, criterio, estado y evidencia; cada operación API tiene enlace al catálogo. | Al añadir/cambiar una capacidad se actualizan catálogo, matriz y pruebas; decisiones pendientes no se presentan como cumplidas. | OBS | Este documento y [Matriz API](matriz-trazabilidad-api.md) |
| RNF-MAN-005 | Revisar dependencias y mantenimiento | Definir proceso de actualización y revisión de vulnerabilidades/licencias del artefacto. | Acordar frecuencia, responsables, herramientas y plazos de tratamiento; el listado de versiones no certifica seguridad ni vigencia. | PROP | [build.gradle][build], [Dockerfile][docker] |

## Decisiones de producto pendientes

Estas decisiones requieren una regla explícita del producto. Hasta acordarlas, el comportamiento del código sirve como evidencia de lo existente, no como aprobación del negocio.

| ID | Decisión | Qué debe quedar definido | Requisitos afectados |
| --- | --- | --- | --- |
| D01 | Reingreso y expulsión de grupos | **Resuelta:** quien sale voluntariamente puede volver por sí mismo a un grupo público activo; para un grupo privado necesita que dueño/creador/admin lo agregue. Quien es expulsado puede solicitar reingreso; la solicitud notifica al creador y solo este la aprueba o rechaza. Los grupos cerrados no aceptan altas. El creador no puede salir ni ser expulsado. | RF-GRU-002 a RF-GRU-005, RF-GRU-017; RNF-SEG-002, RNF-DAT-001 |
| D02 | Planes, compra y límites | Quién activa FAMILY/PREMIUM; cuota PREMIUM de compartidos; acceso diario/semanal/mensual; descenso de plan y recursos excedidos; vencimiento y renovación del servicio. | RF-PLA-001 a RF-PLA-007, RF-COM-001 |
| D03 | Alcance de cuidadores y compartidos | Permisos exactos para ficha con barrio, ubicación actual, ruta reciente, historial, distancias, geocercas y alertas. Plan del dueño o del lector; aceptación del token con identidad; alta/cambio de contraseña del invitado; aceptación segura desde correo. | RF-ACC-004, RF-TMP-001 a RF-TMP-009, RF-LOC-001 a RF-LOC-008 |
| D04 | Visibilidad y datos publicados | **Resuelto para grupos:** se permite crear grupos aunque la mascota no esté `LOST`; el creador siempre es miembro activo; el grupo inicia `ACTIVE` y solo el creador puede cerrarlo/reabrirlo. Pendiente: datos de contacto visibles, quién puede listar miembros/buscar candidatos e inclusión del dueño sin membresía en listados de descubrimiento. | RF-GRU-006 a RF-GRU-016, RF-PAS-002, RF-MAS-010, RNF-SEG-007 |
| D05 | Negociación y ejecución de paseos | Tabla de transiciones para cada actor; aceptación previa al inicio; precio definitivo; cancelación después de iniciar; repetición de acciones; rol dueño/paseador en una cuenta y efecto de desactivar un perfil. | RF-PAS-006, RF-SOL-001 a RF-SOL-007, RF-PSE-001 a RF-PSE-006 |
| D06 | Semántica de geocerca y alertas | Borde de zona, tolerancia a ruido GPS, permanencia fuera, repetición/deduplicación, grupos destinatarios y si se exige estado LOST para avisar a voluntarios. | RF-GEO-006, RF-GEO-007, RF-ALT-001, RF-NOT-005 |
| D07 | Validez y entrega de GPS | **Política aplicada:** solo `gpsValid=true`, coordenadas dentro de rango, timestamps no más de 10 min antiguos ni 5 min futuros, y timestamp estrictamente posterior al último guardado por mascota; ACK positivo tras commit o rechazo deliberado, falla de infraestructura sin ACK positivo. Pendiente: identidad criptográfica, tolerancia a pérdida por caída abrupta y coordinación de orden con múltiples réplicas. | RF-GPS-003 a RF-GPS-010, RNF-CON-002/006, RNF-SEG-013 |
| D08 | Conservación y privacidad de datos | Plazos por tipo de dato, acceso a logs, consentimiento para contactos/geolocalización, anonimización, eliminación y tratamiento de copias de respaldo. | RNF-SEG-007, RNF-DAT-005, RNF-CON-005, RNF-OBS-004 |
| D09 | Objetivos medibles de calidad | Latencias, usuarios/terminales concurrentes, frecuencia de posición, disponibilidad, pérdida admisible, RPO/RTO y entorno de medición. Cada objetivo necesita valor, responsable y prueba. | RNF-REN-001, RNF-REN-009, RNF-CON-001, RNF-CON-005 |
| D10 | Topología de producción | Una o varias instancias, exposición TCP/HTTP, coordinación de cachés/tareas/broker, separación de GPS y estrategia de despliegue/recuperación. | RNF-OPE-002 a RNF-OPE-006, RNF-CON-006 |
| D11 | Cobertura geográfica y formatos | Significado de resolved fuera de Medellín; idioma/Unicode de informes; zonas horarias mostradas; frescura de posición; rangos inválidos en reportes de transporte. | RF-LOC-005, RF-LOC-008, RF-TRA-007, RNF-USA-004, RNF-USA-005 |
| D12 | Borrado con historial relacionado | Eliminar, anonimizar, conservar o bloquear mascota/paseador/geocerca con alertas, grupos, posiciones, solicitudes, paseos y eventos asociados. | RF-MAS-005, RF-PAS-007, RF-TRA-010, RNF-DAT-006 |

## Brechas identificadas en la revisión

La prioridad indica el orden sugerido para analizar/corregir, no una severidad de incidente medida en producción. Son hallazgos estáticos; las pruebas indicadas son las necesarias para cerrar cada brecha. No constituyen una auditoría exhaustiva de seguridad.

| ID | Prioridad | Hallazgo y evidencia | Comprobación necesaria para cerrarlo |
| --- | --- | --- | --- |
| B01 | Resuelta en código; validación pendiente | `joinSearchGroupWithResult` crea una solicitud para miembros revocados, y el evento notifica al creador tras el commit. Solo el creador puede listar, aprobar o rechazar solicitudes; aprobar reactiva la membresía. [VolunteerService][groups], [SearchGroupController][groups-api]; RF-GRU-004/005. | Probar solicitudes repetidas, notificación, autorización exclusiva del creador, aprobación/rechazo, grupo cerrado y reingreso tras rechazo. |
| B02 | Resuelta en código; validación pendiente | Cambios de membresía bloquean el registro del grupo y existe unicidad usuario/grupo; el esquema elimina duplicados previos antes de crear el índice único. [Volunteer][volunteer-entity], [schema.sql][schema]; RNF-DAT-001. | Dos joins simultáneos deben conservar una membresía, conteos consistentes y una sola notificación; verificar con PostgreSQL real. |
| B03 | Alta | `PetService.getLocations` comprueba ubicación para entregar historial; barrio/distancia y el mapper pueden devolver datos derivados de ubicación usando acceso general. [PetService][pets], [PetMapper][pet-map]; RF-TMP-009. | Matriz de combinaciones de canViewLocation/canViewHistory/canViewAlerts cubre cada ruta y cada campo derivado, incluso con permisos false. |
| B04 | Media | La consulta agregada de últimas posiciones no incluye temporales; alertas contempla dueño/temporales, sin acceso compartido permanente. [LocationRepository][locations-repo], [AlertService][alerts]. | Acordar D03 y comprobar que listas agregadas y consultas individuales respetan exactamente el mismo alcance aprobado. |
| B05 | Resuelta en código; validación pendiente | El join y la incorporación por gestor publican eventos atendidos de forma asíncrona después del commit; la notificación se omite si la membresía ya no está activa. [VolunteerService][groups], [VolunteerNotificationService][volunteer-notify]; RNF-CON-003. | Verificar con proveedor lento, rollback y fallos de entrega; el evento en memoria no garantiza reintento durable ante caída del proceso. |
| B06 | Media | Pérdida llama tres veces a notifyOwner, dos con nombre fijo; T07 espera notifyLostPet, cuya llamada está comentada. Además, se accede a pet.getId() antes del control de null. [LostPetNotificationService][lost-notify]. | Mascota real produce los mensajes acordados sin duplicados/nombre fijo; ID inexistente no causa NullPointerException; actualizar y ejecutar T07. |
| B07 | Alta | Cotizar acepta cualquier enum de estado; decisión no comprueba estado de origen; inicio permite NEGOTIATING y completar no exige transición válida. [WalkerMarketplaceService][market]; D05. | Probar tabla de transiciones por actor, solicitud/paseo consistente, precio y repetición; incluir cancelación tras iniciar. |
| B08 | Alta | Registro acepta plan solicitado; PREMIUM no aplica límite de compartidos pese al 20 en PlanPolicy; resumen semanal sin diferencia de planes y comparación mensual ausente. [AuthController][auth], [PetAccessService][shares], [PetWalkDistanceService][distance]. | Acordar D02 y probar activación autorizada, límites y beneficios de cada plan, incluida concurrencia y descenso. |
| B09 | Alta | /ws es público, origen *, broker /topic sin autorización de suscripción identificada; alerts/{ownerId} contiene información de alertas. [SecurityConfig][security], [WebSocketConfig][ws]. | Cliente anónimo y cuenta ajena no reciben el topic; titular autorizado sí; probar handshake, CONNECT y SUBSCRIBE. |
| B10 | Media | Paginación incompleta o en memoria; exportaciones/transporte sin límites de puntos; mapeo de mascotas puede hacer consultas/geocodificación por elemento. [PetService][pets], [PetMapper][pet-map], [TransitReportService][transit-reports]. | Medir consultas, memoria y latencia con volumen acordado; limitar/procesar por lotes y comprobar continuidad de páginas. |
| B11 | Media | Validación desigual de campos/longitudes/coordenadas/montos y RuntimeException general convertido a 400; exportación CSV sin protección específica de fórmulas y PDF sin Unicode completo. [ApiExceptionHandler][errors], [WalkerMarketplaceService][market], [PetService][pets]. | Casos inválidos producen errores controlados sin escrituras; probar exportaciones con texto especial y caracteres españoles. |
| B12 | Media | Listado descubrible depende de público/membresía activa, mientras detalle privado también acepta dueño/creador. Listado por mascota y consulta de miembros tienen reglas distintas. [SearchGroupRepository][groups-repo], [SearchGroupController][groups-api]. | Casos de dueño/creador sin membresía y usuario de grupo público devuelven visibilidad consistente con D04. |
| B13 | Media | Invitación construye ruta relativa; sendEmail no confirma entrega y captura fallos. Crear concesión no demuestra que el cuidador recibió enlace usable. [TemporaryPetAccessService][temporary], [NotificationService][notify]. | Correo recibido contiene URL absoluta válida del entorno; acordar qué informa UI si entrega falla y cómo reenviar. |
| B14 | Media | UI exige PREMIUM para recurrencia, mientras backend permite ADMIN independientemente del plan. [app.js][ui] `updateTemporaryAccessFormState`, [TemporaryPetAccessService][temporary]. | ADMIN/BASIC y ADMIN/FAMILY pueden usar la capacidad autorizada; USER/FAMILY sigue sin recurrencia. |
| B15 | Alta | El proyecto Transit separado no incluye configuración Spring Security ni autorización por rol en sus controladores; operaciones de flota y ubicaciones quedan sin una barrera de autenticación identificada. [Transit project](../../transit/README.md), controladores Transit. | Definir autenticación/autorización, proteger todas las rutas y probar llamadas sin sesión y con roles no autorizados antes de desplegar el servicio. |
| B16 | Alta; mitigación implementada, validación pendiente | El ACK de ubicación ahora espera persistencia o rechazo deliberado; rechazo del executor/cola y fallo de BD no dan resultado positivo. Se agregaron métricas de ubicaciones entregadas al servicio/persistidas/rechazadas/fallidas y drenaje de hasta 30 s; el listener cierra clientes antes de parar consumidores. La cola opcional sigue siendo volátil y puede perder trabajo ante terminación abrupta. [GpsTcpServer][gps-server], [GpsMessageHandler][gps-handler], [TopinLocationHandler][topin-location], [GpsIngestionService][ingestion], [AlertConfig][executors]; RNF-CON-002/006. | Saturar executor/cola, provocar fallo de BD y detener bajo carga; validar ACK/error por protocolo, reconciliar contadores y confirmar timeout de drenaje. |
| B17 | Alta; mitigación implementada, validación pendiente | V41, Topin y ASCII pasan por validación común de fix, rango de coordenadas, antigüedad y orden estricto por mascota; solo una ubicación guardada solicita geocerca. El bloqueo de orden es local a una instancia. [V41ProtocolDecoder][v41-decoder], [GpsIngestionService][ingestion], [TopinLocationHandler][topin-location]; RF-GPS-009, D07. | Probar invalid fixes, límites, duplicados, timestamps retrasados y eventos de geocerca; si hay varias réplicas, verificar coordinación de escritura. |
| B18 | Media; mitigación implementada, validación pendiente | Los terminales registrados se precargan; misses/expiraciones consultan con executor acotado y Netty no ejecuta SQL. JT808 espera lookup fuera del event loop; Topin falla cerrado y puede cerrar el primer login mientras carga caché. [GpsMessageHandler][gps-handler], [GpsIngestionService][ingestion], [TopinSessionHandler][topin-session], [AlertConfig][executors]; RNF-REN-004. | Simular caché fría/expirada, BD lenta y lookup saturado con conexiones concurrentes; comprobar que los event loops siguen respondiendo y que dispositivos Topin reconectan tras cargar la caché. |

## Inventario de pruebas existentes

Se identificaron 18 clases. Su presencia no implica cobertura completa ni ejecución exitosa. T07 presenta una divergencia visible con el código; T04 incluye pruebas unitarias del ciclo de solicitud, aprobación y rechazo; se ejecutaron 15 pruebas de `VolunteerServiceTest` con éxito. Aún falta validación HTTP y concurrencia con PostgreSQL real.

| Clave | Archivo | Evidencia que aporta al catálogo |
| --- | --- | --- |
| T01 | [PetImageServiceTest][t01] | Reemplazo/conservación de imagen, rechazo de otro usuario y límites configurados. |
| T02 | [PetImageUploadTest][t02] | Operaciones de mascotas JSON/multipart y conservación de imagen. |
| T03 | [PetImageUploadLimitIntegrationTest][t03] | Límites multipart de archivo/solicitud y respuesta 413 con servidor HTTP de prueba. |
| T04 | [VolunteerServiceTest][t04] | Creación con miembros, reactivación, join repetido, desconocido en privado, edición y búsqueda de candidatos a nivel de servicio. No acredita las restricciones adicionales del controlador. |
| T05 | [LocationServiceTest][t05] | Interpretación de fecha UTC/local e inválida; no prueba autorización de historial. |
| T06 | [NotificationServiceTest][t06] | Enrutamiento a SMS/WhatsApp cuando está habilitado/deshabilitado; no prueba entrega externa. |
| T07 | [LostPetNotificationServiceTest][t07] | Expectativa de delegación a notifyLostPet, divergente de implementación revisada. |
| T08 | [LoginDemoDataServiceTest][t08] | Aislamiento de cuenta demo, habilitación, repetición y conservación de mascotas reales. |
| T09 | [DemoLoginTest][t09] | Preparar demo solo después de validar contraseña y antes de emitir token. |
| T10 | [DemoGpsSimulatorTest][t10] | Movimientos sintéticos, aislamiento de cuentas/terminales y alerta interna sin notificación real. |
| T11 | [GpsProtocolDetectorTest][t11] | Selección de protocolo y tratamiento de fragmentación/bytes iniciales. |
| T12 | [Jt808ConnectionTest][t12] | Flujo JT808 clásico/2019, identidad, registro/heartbeat, rechazos, secuencias y ACK. |
| T13 | [Jt808DeviceTimeZoneTest][t13] | Conversión por dispositivo UTC/Bogotá/Shanghai y cambio de fecha. |
| T14 | [Jt808TimeZonesTest][t14] | Resolución/configuración de zonas por terminal. |
| T15 | [GpsMessageHandlerTest][t15] | Extracción de batería en comandos ASCII y validación de valores. |
| T16 | [V41ProtocolDecoderTest][t16] | Decodificación, cabeceras y campos de paquetes V41/JT808. |
| T17 | [BsjBatteryDecoderTest][t17] | Extensiones de batería/IMEI, valores corruptos y recorrido hasta persistencia/mapeo con dobles de prueba. |
| T18 | [PettrackerApplicationTests][t18] | Carga del contexto Spring; no sustituye pruebas de reglas de negocio. |

No se identificaron clases dedicadas para permisos temporales, planes/cuotas, transiciones de paseos, chat, suscripciones WebSocket, reglas completas de geocerca, administración o transporte. Tampoco pruebas de carga, restauración ni integración PostgreSQL/PostGIS mediante Testcontainers.

## Uso y mantenimiento del catálogo

El [plan de revisión del código](plan-revision-codigo.md) organiza el recorrido por funcionalidades, empezando por la autorización de ingreso y reingreso a grupos.

1. Resolver D02–D12 con la persona responsable de producto y registrar regla aprobada, fecha y responsable.
2. Convertir el criterio de cada requisito en casos verificables, empezando por las brechas de prioridad alta. No cambiar OBS/PAR a «validado» únicamente porque compile.
3. Registrar ejecución, versión y resultado de pruebas. Una prueba unitaria no acredita por sí sola concurrencia, entrega externa ni comportamiento en producción.
4. Mantener actualizados este catálogo y la [matriz de las 78 operaciones HTTP](matriz-trazabilidad-api.md). Los procesos GPS, tareas, navegador, operación y compromisos comerciales permanecen aquí aunque no tengan endpoint propio.
5. Al cambiar reglas, conservar identificadores y actualizar código, documentación y pruebas relacionadas. Los documentos de arquitectura y guía de estudio complementan este catálogo.

## Fuentes

Los enlaces a código señalan archivos del mismo repositorio. Los nombres de métodos en las filas permiten localizar la regla sin depender de números de línea que cambian con cada edición.

[readme]: ../README.md
[design]: firuapp-design-document.md
[commercial]: planes-y-limites.md
[build]: ../build.gradle
[docker]: ../Dockerfile
[railway]: ../railway.json
[gradle-wrapper]: ../gradle/wrapper/gradle-wrapper.properties
[config]: ../src/main/resources/application.yml
[schema]: ../src/main/resources/schema.sql
[ui]: ../src/main/resources/static/app.js
[html]: ../src/main/resources/static/index.html
[css]: ../src/main/resources/static/app.css
[auth]: ../src/main/java/com/example/pettracker/controller/AuthController.java
[users-repo]: ../src/main/java/com/example/pettracker/repository/UserRepository.java
[jwt]: ../src/main/java/com/example/pettracker/security/JwtProvider.java
[jwt-filter]: ../src/main/java/com/example/pettracker/security/JwtAuthFilter.java
[security]: ../src/main/java/com/example/pettracker/security/SecurityConfig.java
[market]: ../src/main/java/com/example/pettracker/service/WalkerMarketplaceService.java
[plans]: ../src/main/java/com/example/pettracker/service/PlanPolicy.java
[user-plans]: ../src/main/java/com/example/pettracker/controller/UserPlanController.java
[user-service]: ../src/main/java/com/example/pettracker/service/UserService.java
[pets]: ../src/main/java/com/example/pettracker/service/PetService.java
[shares]: ../src/main/java/com/example/pettracker/service/PetAccessService.java
[groups]: ../src/main/java/com/example/pettracker/service/VolunteerService.java
[geofence]: ../src/main/java/com/example/pettracker/service/GeofencingService.java
[distance]: ../src/main/java/com/example/pettracker/service/PetWalkDistanceService.java
[pet-api]: ../src/main/java/com/example/pettracker/controller/PetController.java
[pets-repo]: ../src/main/java/com/example/pettracker/repository/PetRepository.java
[pet-map]: ../src/main/java/com/example/pettracker/mapper/PetMapper.java
[images]: ../src/main/java/com/example/pettracker/service/ImageUploadLimits.java
[ingestion]: ../src/main/java/com/example/pettracker/service/GpsIngestionService.java
[lost-notify]: ../src/main/java/com/example/pettracker/service/LostPetNotificationService.java
[shares-api]: ../src/main/java/com/example/pettracker/controller/PetAccessController.java
[alerts]: ../src/main/java/com/example/pettracker/service/AlertService.java
[temporary]: ../src/main/java/com/example/pettracker/service/TemporaryPetAccessService.java
[notify]: ../src/main/java/com/example/pettracker/service/NotificationService.java
[temporary-api]: ../src/main/java/com/example/pettracker/controller/TemporaryPetAccessController.java
[locations-repo]: ../src/main/java/com/example/pettracker/repository/LocationRepository.java
[locations]: ../src/main/java/com/example/pettracker/service/LocationService.java
[neighborhood]: ../src/main/java/com/example/pettracker/service/NeighborhoodLookupService.java
[groups-api]: ../src/main/java/com/example/pettracker/controller/SearchGroupController.java
[groups-repo]: ../src/main/java/com/example/pettracker/repository/SearchGroupRepository.java
[group-map]: ../src/main/java/com/example/pettracker/mapper/SearchGroupMapper.java
[volunteers-repo]: ../src/main/java/com/example/pettracker/repository/VolunteerRepository.java
[volunteer-entity]: ../src/main/java/com/example/pettracker/entity/Volunteer.java
[rejoin-entity]: ../src/main/java/com/example/pettracker/entity/VolunteerRejoinRequest.java
[group-chat]: ../src/main/java/com/example/pettracker/service/SearchGroupChatService.java
[walk-message]: ../src/main/java/com/example/pettracker/entity/WalkMessage.java
[gps-server]: ../src/main/java/com/example/pettracker/gps/GpsTcpServer.java
[gps-detector]: ../src/main/java/com/example/pettracker/gps/GpsProtocolDetector.java
[v41-frame]: ../src/main/java/com/example/pettracker/gps/protocol/v41/V41FrameDecoder.java
[topin-frame]: ../src/main/java/com/example/pettracker/topin/TopinFrameDecoder.java
[v41-decoder]: ../src/main/java/com/example/pettracker/gps/protocol/v41/V41ProtocolDecoder.java
[gps-handler]: ../src/main/java/com/example/pettracker/gps/GpsMessageHandler.java
[jt-replies]: ../src/main/java/com/example/pettracker/gps/protocol/v41/Jt808Replies.java
[topin-session]: ../src/main/java/com/example/pettracker/topin/TopinSessionHandler.java
[topin-location]: ../src/main/java/com/example/pettracker/gps/TopinLocationHandler.java
[location-map]: ../src/main/java/com/example/pettracker/mapper/LocationMapper.java
[jt-time]: ../src/main/java/com/example/pettracker/gps/Jt808TimeZones.java
[time]: ../src/main/java/com/example/pettracker/service/AppTimeService.java
[sms]: ../src/main/java/com/example/pettracker/service/TwilioSmsProvider.java
[ws]: ../src/main/java/com/example/pettracker/config/WebSocketConfig.java
[volunteer-notify]: ../src/main/java/com/example/pettracker/service/VolunteerNotificationService.java
[transit-api]: ../../transit/src/main/java/com/example/pettracker/transit/controller/TransitVehicleController.java
[vehicles]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitVehicleService.java
[transit-location]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitLocationService.java
[transit-ingestion]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitLocationIngestionService.java
[transit-reports]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitReportService.java
[transit-geofence]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitGeofencingService.java
[transit-geofence-api]: ../../transit/src/main/java/com/example/pettracker/transit/controller/TransitGeofenceController.java
[transit-groups]: ../../transit/src/main/java/com/example/pettracker/transit/service/TransitSearchGroupService.java
[transit-groups-api]: ../../transit/src/main/java/com/example/pettracker/transit/controller/TransitSearchGroupController.java
[metrics]: ../src/main/java/com/example/pettracker/service/AdminMetricsService.java
[http-metrics]: ../src/main/java/com/example/pettracker/service/HttpRequestMetricsService.java
[demo-walkers]: ../src/main/java/com/example/pettracker/service/DemoWalkerDataService.java
[demo-login]: ../src/main/java/com/example/pettracker/service/LoginDemoDataService.java
[demo-account]: ../src/main/java/com/example/pettracker/service/DemoAccount.java
[demo-gps]: ../src/main/java/com/example/pettracker/service/DemoGpsSimulator.java
[errors]: ../src/main/java/com/example/pettracker/controller/ApiExceptionHandler.java
[executors]: ../src/main/java/com/example/pettracker/config/AlertConfig.java
[app-cache]: ../src/main/java/com/example/pettracker/config/AppCacheConfig.java
[gps-cache]: ../src/main/java/com/example/pettracker/config/GpsCacheConfig.java
[railway-validator]: ../src/main/java/com/example/pettracker/config/RailwayDeploymentValidator.java
[railway-env]: ../src/main/java/com/example/pettracker/config/RailwayDatabaseEnvironmentPostProcessor.java
[watchdog]: ../src/main/java/com/example/pettracker/service/JdbcConnectionWatchdog.java
[kafka-producer]: ../src/main/java/com/example/pettracker/gps/GpsKafkaProducer.java
[kafka-consumer]: ../src/main/java/com/example/pettracker/gps/GpsKafkaConsumer.java
[t01]: ../src/test/java/com/example/pettracker/service/PetImageServiceTest.java
[t02]: ../src/test/java/com/example/pettracker/controller/PetImageUploadTest.java
[t03]: ../src/test/java/com/example/pettracker/controller/PetImageUploadLimitIntegrationTest.java
[t04]: ../src/test/java/com/example/pettracker/service/VolunteerServiceTest.java
[t05]: ../src/test/java/com/example/pettracker/service/LocationServiceTest.java
[t06]: ../src/test/java/com/example/pettracker/service/NotificationServiceTest.java
[t07]: ../src/test/java/com/example/pettracker/service/LostPetNotificationServiceTest.java
[t08]: ../src/test/java/com/example/pettracker/service/LoginDemoDataServiceTest.java
[t09]: ../src/test/java/com/example/pettracker/controller/DemoLoginTest.java
[t10]: ../src/test/java/com/example/pettracker/service/DemoGpsSimulatorTest.java
[t11]: ../src/test/java/com/example/pettracker/gps/GpsProtocolDetectorTest.java
[t12]: ../src/test/java/com/example/pettracker/gps/Jt808ConnectionTest.java
[t13]: ../src/test/java/com/example/pettracker/gps/Jt808DeviceTimeZoneTest.java
[t14]: ../src/test/java/com/example/pettracker/gps/Jt808TimeZonesTest.java
[t15]: ../src/test/java/com/example/pettracker/gps/GpsMessageHandlerTest.java
[t16]: ../src/test/java/com/example/pettracker/gps/protocol/v41/V41ProtocolDecoderTest.java
[t17]: ../src/test/java/com/example/pettracker/gps/protocol/v41/BsjBatteryDecoderTest.java
[t18]: ../src/test/java/com/example/pettracker/PettrackerApplicationTests.java
