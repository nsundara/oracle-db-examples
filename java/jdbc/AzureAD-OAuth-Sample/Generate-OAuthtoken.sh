#!/bin/bash
# The script demostrates the use of the Azure CLI tool to obtain an OAuth2 token for a 
# service principle, and use that to establish connection to the Oracle Autonomous Database. 
# how to configure SQLcl to authenticate with that token.

# Log in as a service principle
# TODO: Oracle's proxy won't allow this request. So this needs to be run with VPN disabled.
#az login --service-principal -u 544dc8bb-6b9f-4636-b20a-fb00927a2ce3 -p $HOME/iam-auth/azure-key-pair.pem --tenant 5b743bc7-c1e2-4d46-b4b5-a32eddac0286 --scope api://3d3e9766-503d-4870-a821-b0544c4224e5/.default --allow-no-subscriptions

# Pre-requisites.
# Make sure to follow these instructions before running the script. 
# 1. Install "Azure CLI" locally on your computer.  
#  	 (https://docs.microsoft.com/en-us/cli/azure/install-azure-cli)
# 2. Install SQLCl locall on your computer. 
#    (https://www.oracle.com/database/sqldeveloper/technologies/sqlcl/)
# 3. Make sure to use 21.6.0.0.1 or 19.15.0.0.1 JDBC driver.  
#    (https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html)
#    Place this in "lib" folder of sqlcl. 
# 4. Copy the following contents to a file and save it as "whoami.sql" 
# 	 Make sure to place this file in the same directory where the script is. 
#
# SELECT
#   SYS_CONTEXT('userenv', 'current_user')
#     AS current_user,
#   SYS_CONTEXT('userenv', 'authenticated_identity')
#     AS authenticated_identity,
#   SYS_CONTEXT('USERENV','AUTHENTICATION_METHOD')
#    AS authentication_method,
#   SYS_CONTEXT('userenv', 'IDENTIFICATION_TYPE')
#    AS identification_type,
#  SYS_CONTEXT('USERENV','ENTERPRISE_IDENTITY')
#    AS enterprise_identity
# FROM
#   sys.dual;
# 
# exit;

# Changes to be made before running the script. 
# 1. Login to your Azure Console and have 
# 2. 
# 3. 

# Store an OAuth access token in an environment variable.
# You can obtain the tenant id and "application id URI" from Azure console. 
echo "Requesting access token from Azure..."
export AZURE_TOKEN=$(az account get-access-token --query accessToken --output tsv --tenant 5b743bc7-c1e2-4d46-b4b5-a32eddac0286 --scope https://oracledevelopment.onmicrosoft.com/d9d0d17b-0d28-4dad-b5c7-28af0d6477f8/.default)
echo 'Access token is stored in the $AZURE_TOKEN environment variable'
echo

# Set TNS_ADMIN for tnsnames.ora and wallet files
# 
export TNS_ADMIN=/Users/testuser/Wallet_TestDB

# Connect with sqlCL, using the access token for authentication

echo "Connecting to Oracle Database with an Azure access token..."
sql "/@testdb_medium?oracle.jdbc.accessToken=$AZURE_TOKEN" @whoami.sql
echo

# Save the access token in a file. JDBC can be configured to use the file with
# the following connection properties:
#   oracle.jdbc.tokenAuthentication=OAUTH
#   oracle.jdbc.tokenLocation=$HOME/.oracle/database/OAuth-token
echo "Saving token to $HOME/.oracle/database/access-token/token ..."
mkdir -p $HOME/.oracle/database/OAuth-token
echo $AZURE_TOKEN > $HOME/.oracle/database/OAuth-token/token
echo "Token file created"
echo

