# This is a simple SQL script that is used to verify
# successful connection to the Oracle Database. 

SELECT
  SYS_CONTEXT('userenv', 'current_user')
    AS current_user,
  SYS_CONTEXT('userenv', 'authenticated_identity')
    AS authenticated_identity,
  SYS_CONTEXT('USERENV','AUTHENTICATION_METHOD')
    AS authentication_method,
  SYS_CONTEXT('userenv', 'IDENTIFICATION_TYPE')
    AS identification_type,
  SYS_CONTEXT('USERENV','ENTERPRISE_IDENTITY')
   AS enterprise_identity
FROM
  sys.dual;

exit;
