import json
import os
from botocore.exceptions import ClientError
from boto3.dynamodb.conditions import Key, Attr
import boto3
import time

def billDueMailSender(event, context):

    domain = os.environ.get("DomainName")

    snsMessage = event['Records'][0]['Sns']['Message']
    print(snsMessage)
    reset_req = json.loads(snsMessage)
    recipient = reset_req["emailId"]
    print(recipient)
    bills = reset_req["dueBills"]
    body = ''
    for link in bills:
        body = '<br>'.join([body, link])

    shouldSend = insert_email_to_dynamodb(recipient)

    if shouldSend:
        send_email(recipient, domain,body)
    else:
        print('no email sent')

def send_email(recipient, domain,body):
    SENDER = "noreply@" + domain

    dueBillLinks = body

    body_text = (
                    dueBillLinks + "\r\n"
                )

    body_html = """<html>
    <head></head>
    <body>
      <h1>Due bills from UserManagement Application</h1>
      <p>
        <br><br>
        """ + dueBillLinks + """
        <br><br>
      </p>
    </body>
    </html>
                """


    trigger_email(recipient, body_html, body_text, "Due Bills", SENDER)



def trigger_email(recipient, BODY_HTML, BODY_TEXT, SUBJECT, SENDER):

    AWS_REGION = 'us-east-1'
    print(AWS_REGION)
    CHARSET = "UTF-8"
    client = boto3.client('ses',region_name=AWS_REGION)

    try:
        response = client.send_email(
            Destination={
                'ToAddresses': [
                    recipient,
                ],
            },
            Message={
                'Body': {
                    'Html': {
                        'Charset': CHARSET,
                        'Data': BODY_HTML,
                    },
                    'Text': {
                        'Charset': CHARSET,
                        'Data': BODY_TEXT,
                    },
                },
                'Subject': {
                    'Charset': CHARSET,
                    'Data': SUBJECT,
                },
            },
            Source=SENDER,
        )
    except ClientError as e:
        print(e.response['Error']['Message'])
    else:
        print("Email sent!"),
        print(response['MessageId'])

def insert_email_to_dynamodb(recipient):
    dynamo_table = "EmailTable"
    dynamodb = boto3.resource('dynamodb',region_name='us-east-1')
    table = dynamodb.Table(dynamo_table)
    dynamo_row = table.query(
        KeyConditionExpression=Key('EmailID').eq(recipient)
    )
    items = dynamo_row['Items']

    if not items:
        response = table.put_item(
        Item={
            'EmailID': recipient,
            'CreationTime' : str(time.time()),
            'ExpirationTime' : str(time.time() + 3600)
            }
        )
        print("record inserted")
        return True
    elif items:
        dt = float(items[0]['CreationTime'])
        currentTime = float(time.time())
        time_diff_minutes = (currentTime - dt)/60
        if time_diff_minutes<=60.0:
            print(" TTL")
        else:
            table.update_item(
            Key={
                'EmailID' : recipient
            },
            UpdateExpression="set CreationTime = :c, ExpirationTime = :e",
            ExpressionAttributeValues={
            ':c': str(time.time()),
            ':e': str(time.time() + 3600)
        },
            ReturnValues="UPDATED_NEW"
        )
            return True
