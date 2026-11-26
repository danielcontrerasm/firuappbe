# Plan de revisión del código por funcionalidad

La primera unidad, **unirse y volver a un grupo de búsqueda**, ya se recorrió y corrigió en código. La política D01 distingue salida voluntaria de expulsión; el reingreso revocado ahora pasa por solicitud al creador. Las pruebas unitarias de servicio ya se ejecutaron correctamente; quedan pendientes las pruebas HTTP, concurrencia con PostgreSQL real y validación manual antes de marcarla verificada.

Fecha de revisión: 21 de noviembre de 2026. Base: [catálogo de requisitos](catalogo-requisitos.md) y [matriz de API](matriz-trazabilidad-api.md), contrastados con el código local. La implementación de membresía y solicitudes de reingreso se actualizó; `VolunteerServiceTest` pasó 15/15 casos. Faltan comprobaciones HTTP, PostgreSQL real y notificaciones con proveedores externos.

## Forma de trabajar

La unidad de revisión será **una acción completa del usuario o del sistema**: por ejemplo, unirse a un grupo, revocar acceso o completar un paseo. Una acción puede corresponder a varios RF. En cada bloque se aborda una acción a la vez, siguiendo sus llamadas y efectos.

| Paso | Trabajo sobre el código | Resultado |
| --- | --- | --- |
| 1. Precisar comportamiento | Identificar actor, entradas, permisos, resultado esperado y decisiones pendientes. | Ejemplos concretos de éxito, rechazo y casos límite; separar regla acordada de comportamiento actual. |
| 2. Recorrer el flujo | Leer entrada HTTP/TCP/tarea, DTO, servicio, repositorios, entidades, restricciones de BD y efectos externos. Incluir la UI cuando invoque esa acción. | Explicación de qué hace cada bloque y dónde se aplica cada regla. |
| 3. Contrastar ramas | Revisar datos ausentes, IDs ajenos, estados previos, repetición, concurrencia cuando corresponda y fallos externos. | Hallazgos con archivo/método, condición que los activa, impacto y requisito afectado. |
| 4. Comprobar lo dudoso | Ejecutar pruebas relevantes y reproducir defectos. Usar depurador cuando valores, ramas o orden real no puedan resolverse por lectura. | Evidencia observable; los dobles de prueba no demuestran restricciones o carreras de PostgreSQL. |
| 5. Preparar corrección | Proponer un cambio pequeño que resuelva el defecto. Si depende de una regla de negocio pendiente, documentar opciones y recomendación. | Cambio y prueba de regresión concretos; una decisión pendiente no se presenta como aprobada. |
| 6. Verificar y registrar | Al implementar, ejecutar pruebas pertinentes y comprobar impactos sobre consumidores inmediatos. | Resultado de pruebas, riesgos restantes, referencias y actualización de la fila del catálogo. |

Seguridad, validación, transacciones, consultas, errores y privacidad se revisan en cada acción. Las mediciones globales de capacidad, recuperación y despliegue tienen además un bloque propio. No se presupone que todas las propuestas RNF ya tengan un objetivo aprobado.

## Orden de revisión

Los bloques 1–13 asignan una revisión principal a cada uno de los **141 RF vinculados al código**. Las dependencias se inspeccionan cuando haga falta, aunque su revisión completa esté en un bloque posterior. La columna «Orden dentro del bloque» define acciones sucesivas, no una única revisión masiva.

| Bloque | Funcionalidades | Orden dentro del bloque | RF con revisión principal | Motivo |
| --- | --- | --- | --- | --- |
| 1 | Membresía en grupos | Join/reingreso → incorporación por gestor → salida → expulsión → concurrencia y notificación de altas. | RF-GRU-001 a RF-GRU-005; RF-GRU-017; RF-NOT-006 | B01, B02 y B05: autorización, consistencia y efectos de una transacción. |
| 2 | Autenticación, planes y administración de usuarios | Registro/plan inicial → login/JWT → identidad/logout → cambio de plan → cada límite y beneficio → listado administrativo. | RF-AUT-001 a RF-AUT-005; RF-PLA-001 a RF-PLA-007; RF-ADM-001 | Identidad y privilegios afectan a todos los módulos; B08 y decisión D02. |
| 3 | Alertas por WebSocket | Conexión → autenticación → autorización de cada suscripción → publicación al destinatario correcto. | RF-NOT-003 | B09: posible exposición de alertas a otra cuenta o a un visitante. |
| 4 | Compartir mascotas y cuidadores | Compartir/listar/revocar → crear invitación → aceptar → vigencia → recurrencia → permisos por dato y consultas agregadas. | RF-ACC-001 a RF-ACC-004; RF-TMP-001 a RF-TMP-009 | B03, B04, B13 y B14: permisos y revocación de acceso a ubicación e historial. |
| 5 | Paseadores, solicitudes y paseos | Alta/aprobación de perfil → solicitud → cotización → decisión → inicio → posiciones → finalización → chat y listados → edición/borrado de perfil. | RF-PAS-001 a RF-PAS-007; RF-SOL-001 a RF-SOL-007; RF-PSE-001 a RF-PSE-006 | B07: estados, actores, precio y coherencia entre solicitud y paseo. |
| 6 | Creación, consulta y chat de grupos | Crear/editar → visibilidad → detalle y listados → miembros/candidatos → chat → imagen y enlace WhatsApp. | RF-GRU-006 a RF-GRU-016; RF-CHA-001 a RF-CHA-002 | B12 y D04: coherencia de permisos y datos expuestos. Reutiliza membresía revisada en bloque 1. |
| 7 | Gestión de mascotas | Crear → listar/consultar → editar → vincular terminal → subir/leer imagen → perdida/encontrada → eliminar con dependencias. | RF-MAS-001 a RF-MAS-010 | Propiedad del recurso, datos, archivos y consecuencias del cambio de estado/borrado. |
| 8 | GPS, ubicaciones e informes | Conexión → tramas/protocolos → ACK → hora/batería → persistencia/colas → posición manual → consultas/rutas → geocodificación → distancias → CSV/PDF. | RF-GPS-001 a RF-GPS-010; RF-LOC-001 a RF-LOC-009 | Correcciones iniciales de ingesta aplicadas y registradas en [revisión funcional](../REVISION_FUNCIONALIDADES.md#revisión-ingesta-gps). B16–B18/D07 mitigadas parcialmente; falta validación compilada, PostgreSQL real, saturación/cierre bajo carga, multi-réplica y el resto de ubicaciones/rutas/informes. |
| 9 | Geocercas y alertas persistidas | Crear cada geometría → listar/borrar → evaluar posición → crear alerta → consultar/cerrar. | RF-GEO-001 a RF-GEO-007; RF-ALT-001 a RF-ALT-003 | Permisos, bordes, ruido/repetición, destinatarios y decisión D06. |
| 10 | Canales y eventos de notificación | Correo → SMS/WhatsApp → pérdida/recuperación → aviso a voluntarios → convocatoria. | RF-NOT-001; RF-NOT-002; RF-NOT-004; RF-NOT-005; RF-NOT-007 | B06 y entrega/fallos de proveedores. Completa los efectos ya inspeccionados en bloques 1, 7 y 9. |
| 11 | Transit (proyecto separado) | Revisar de forma independiente el servicio hermano `../transit`, su persistencia, seguridad, notificaciones y despliegue. | RF-TRA-001 a RF-TRA-015 | Su README advierte que la autenticación FiruApp no fue trasladada y las notificaciones aún son logs. |
| 12 | Panel web y demostración | Sesión/roles → cada formulario y su API → mapa/posiciones → exportaciones → refresco/tema → datos demo al arrancar/login/tareas. | RF-WEB-001 a RF-WEB-011; RF-DEM-001 a RF-DEM-003 | Verificar la experiencia completa y las diferencias UI/backend; aislamiento de datos sintéticos. |
| 13 | Métricas administrativas | Autorización → fuentes de métricas → significado y reinicio de contadores → presentación. | RF-ADM-002 | Que las señales operativas permitan detectar problemas y no induzcan conclusiones incorrectas. |
| 14 | Calidad y operación del sistema | Consolidar controles revisados → consultas/carga/colas → migraciones → retención/restauración → despliegue/cierre → accesibilidad y mantenimiento. | RNF del catálogo: 57 en total | Completar mediciones y escenarios que no se demuestran revisando un único endpoint. Acordar objetivos pendientes antes de evaluarlos. |

En el bloque 14 se comprueba la cobertura de RNF-SEG-001 a RNF-SEG-014, RNF-REN-001 a RNF-REN-009, RNF-CON-001 a RNF-CON-006, RNF-DAT-001 a RNF-DAT-007, RNF-OBS-001 a RNF-OBS-005, RNF-OPE-001 a RNF-OPE-006, RNF-USA-001 a RNF-USA-005 y RNF-MAN-001 a RNF-MAN-005. Las verificaciones específicas ya ejecutadas se reutilizan; solo se amplían cuando falta evidencia.

RF-FUT-001 a RF-FUT-008 y RF-COM-001 a RF-COM-003 se conservan como alcance futuro o externo. No tienen una implementación operativa que pueda darse por revisada en este recorrido; cualquier desarrollo asociado requiere su propia definición.

## Primera unidad: unirse o volver a un grupo

**Entrada:** POST /api/search-groups/{searchGroupId}/join. **Método principal:** `VolunteerService.joinSearchGroupWithResult`. **RF principales:** RF-GRU-001, RF-GRU-005 y RF-GRU-017.

Objetivo: determinar si cada actor puede entrar, si la respuesta representa correctamente el resultado y si la operación mantiene una sola membresía lógica con efectos coherentes.

| Orden de lectura | Código | Qué vamos a comprobar |
| --- | --- | --- |
| 1 | [SearchGroupController.java](../src/main/java/com/example/pettracker/controller/SearchGroupController.java), método `join` | Identidad obtenida de la sesión, searchGroupId recibido, respuesta 200/201 y campos alreadyMember/reactivated. |
| 2 | [VolunteerService.java](../src/main/java/com/example/pettracker/service/VolunteerService.java), `joinSearchGroupWithResult/canAccessSearchGroup` | Consulta de usuario/grupo/membresía y autorización de cada rama antes de modificar datos. |
| 3 | [VolunteerService.java](../src/main/java/com/example/pettracker/service/VolunteerService.java), `addVolunteerMembership`; [VolunteerRepository.java](../src/main/java/com/example/pettracker/repository/VolunteerRepository.java) | Reutilización de registro, active, joinedAt, colección del grupo y consultas repetidas. |
| 4 | [Volunteer.java](../src/main/java/com/example/pettracker/entity/Volunteer.java), [SearchGroup.java](../src/main/java/com/example/pettracker/entity/SearchGroup.java), [schema.sql](../src/main/resources/schema.sql) | Relaciones, restricciones de unicidad, transacción y dos intentos simultáneos. |
| 5 | [VolunteerService.java](../src/main/java/com/example/pettracker/service/VolunteerService.java), `leaveSearchGroup/removeVolunteer/addVolunteer` | De dónde viene active=false y cómo se autoriza una reincorporación. Inspección de dependencias para entender el join. |
| 6 | [VolunteerNotificationService.java](../src/main/java/com/example/pettracker/service/VolunteerNotificationService.java), notificaciones de join/reingreso; [SearchGroupChatService.java](../src/main/java/com/example/pettracker/service/SearchGroupChatService.java) | Notificación tras commit, solicitudes repetidas y consecuencias de la membresía para leer/escribir chat. |
| 7 | [VolunteerServiceTest.java](../src/test/java/com/example/pettracker/service/VolunteerServiceTest.java) y pruebas HTTP/BD que hagan falta | 15 pruebas unitarias pasaron; faltan pruebas HTTP, PostgreSQL real y proveedor de notificaciones para cubrir el contrato completo. |

### Casos que deben quedar explicados y comprobados

| Caso | Resultado que se revisará |
| --- | --- |
| Visitante sin sesión | La seguridad HTTP no permite ejecutar join. |
| Usuario común, grupo PUBLIC, sin membresía | Crea una membresía activa; 201, alreadyMember=false, reactivated=false. |
| Miembro ya activo | Responde 200, alreadyMember=true y reactivated=false; no crea otra membresía ni repite aviso. |
| Usuario común, grupo PRIVATE, sin membresía | Rechaza sin revelar existencia del grupo, actualmente con 404. |
| Usuario común, grupo PRIVATE, salida voluntaria | El join propio no reactiva la membresía; requiere que dueño/creador/admin lo incorpore. |
| Miembro revocado intenta volver | POST join crea una solicitud PENDING y responde 202; los intentos repetidos reutilizan la solicitud pendiente. |
| Creador revisa solicitud | Solo el creador puede listar, aprobar o rechazar. Aprobar reactiva la membresía; rechazar la mantiene revocada. Ambos resultados notifican al solicitante. |
| Admin, dueño u otro miembro intenta decidir | Solo funciona si esa persona también es el creador; los demás reciben 404. |
| Dueño de mascota, creador o administrador | Verificar su autorización propia aunque no tenga membresía activa. La mera inactividad no debe confundirse con ausencia de todos los permisos. |
| Usuario que salió voluntariamente, grupo PUBLIC activo | Puede volver por join. Si fue revocado, el join siempre crea solicitud, aunque el grupo sea público. |
| Grupo cerrado con solicitud pendiente | El creador puede consultar/rechazar; aprobar requiere reabrir primero el grupo. |
| Grupo inexistente | Responde 404 sin guardar ni notificar. |
| Dos joins simultáneos | Una membresía lógica y respuestas controladas; comprobar en BD real. Un mock de save no acredita este resultado. |
| Fallo de transacción o proveedor lento | No anunciar un alta que se revierte; revisar si el envío mantiene abierta la transacción. |

### Decisión aplicada

**D01 resuelta:** una salida voluntaria conserva `revoked=false`; se permite volver por join en público activo o mediante alta de un gestor en privado. Una expulsión conserva `revoked=true`; el miembro crea una solicitud y solo el creador la aprueba o rechaza. La aprobación está bloqueada si el grupo está cerrado.

El esquema marca como revocadas las membresías inactivas preexistentes durante la primera actualización, ya que los datos anteriores no distinguen expulsión de salida voluntaria. Esos miembros tendrán que solicitar reingreso al creador.

La serialización por grupo, el índice único de membresía y las notificaciones tras commit también se implementaron. Las 15 pruebas de `VolunteerServiceTest` pasaron; faltan comprobaciones HTTP y carreras con PostgreSQL real, además de confirmar correo/SMS con proveedores externos.

## Registro y criterio de cierre

Cada unidad tendrá un registro breve con:

- Acción, RF/RNF relacionados y archivos/métodos recorridos.
- Regla esperada y decisiones de negocio utilizadas.
- Hallazgos: condición de reproducción, efecto, evidencia y prioridad.
- Corrección propuesta o aplicada, según avance del trabajo.
- Pruebas ejecutadas y resultado; diferenciar pruebas pendientes o bloqueadas.
- Pendientes concretos y siguiente unidad.

Estados de seguimiento: **Pendiente**, **En revisión**, **Pendiente de decisión**, **Revisada con hallazgos** y **Verificada**. Se puede avanzar en unidades independientes mientras una decisión sigue pendiente. «Verificada» exige haber comprobado los criterios aplicables, corregido los defectos que impidan cumplirlos y registrado la evidencia; leer el código o compilar no basta.

El estado de seguimiento pertenece al trabajo de revisión. Los estados OBS/PAR/DEC/PEN/PROP/EXT del catálogo siguen describiendo la situación del requisito y se actualizan cuando exista evidencia.

Primera unidad implementada, pendiente de verificación: **join/reingreso y solicitud de aprobación**. Siguiente unidad prevista: **incorporación de un miembro por el gestor**, RF-GRU-002.
