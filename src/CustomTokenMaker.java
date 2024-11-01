import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.modes.JavaTokenMaker;
import javax.swing.text.Segment;

public class CustomTokenMaker extends JavaTokenMaker {

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        Token firstToken = super.getTokenList(text, initialTokenType, startOffset);

        Token currentToken = firstToken;
        while (currentToken != null && currentToken.isPaintable()) {
            String tokenText = currentToken.getLexeme();
            
            if (tokenText.contains("::")) 
                currentToken.setType(Token.RESERVED_WORD);
            if (tokenText.contains("#")) 
                currentToken.setType(Token.PREPROCESSOR);

            currentToken = currentToken.getNextToken();
        }

        return firstToken;
    }
}
