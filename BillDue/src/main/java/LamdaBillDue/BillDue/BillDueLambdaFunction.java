package LamdaBillDue.BillDue;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.Body;
import com.amazonaws.services.simpleemail.model.Content;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.Message;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;

public class BillDueLambdaFunction implements RequestHandler<SNSEvent, Object> {

	private DynamoDB dynamodb;
	private final String tableName = "EmailTable";
	private Regions region = Regions.US_EAST_1;
	String domain = System.getenv("DomainName");
	String from = "UserManagementSystem@" + domain;
	String subject = "List of bills to get due";
	private String body= "Hello";

	int seconds_in_sixty_minute = 60 * 60;
	long secondsSinceEpoch = Instant.now().getEpochSecond();
	long expirationTime = secondsSinceEpoch + seconds_in_sixty_minute;
	AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard().withRegion(region).build();

	public Object handleRequest(SNSEvent request, Context context) {

		final String toEmailId = request.getRecords().get(0).getSNS().getMessage();
		Table table = dynamodb.getTable(tableName);
		if (table != null) {
			Item emailId = table.getItem("EmailID", toEmailId);
			if (emailId == null || (emailId != null
					&& secondsSinceEpoch > Long.parseLong(emailId.get("ExpirationTime").toString()))) {
				Item insertItem = new Item().withPrimaryKey("EmailID", toEmailId)
						.withNumber("ExpirationTime", secondsSinceEpoch);
				table.putItem(insertItem);
				Content subjectContent = new Content().withData(subject);
				Content textbody = new Content().withData(body);
				Body body = new Body().withText(textbody);
				Message message = new Message().withSubject(subjectContent).withBody(body);
				SendEmailRequest emailRequest = new SendEmailRequest()
						.withDestination(new Destination().withToAddresses(toEmailId)).withMessage(message)
						.withSource(from);
				SendEmailResult response = client.sendEmail(emailRequest);
			} else {
				context.getLogger().log(emailId.toJSON() + "Email Already sent!");
			}

		}
		return null;
	}

	private void initDynamoDbClient() {
		AmazonDynamoDBClient client = new AmazonDynamoDBClient();
		client.setRegion(Region.getRegion(region));
		this.dynamodb = new DynamoDB(client);
	}
}
