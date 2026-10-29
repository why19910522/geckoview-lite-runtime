package dev.geckolite.runtime;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Bounded signed-metadata parser: rejects duplicate fields, fractions and overflow. */
final class StrictJson {
  private final String text;
  private int pos;
  private StrictJson(String text) { this.text=text; }
  static Map<String,Object> object(byte[] utf8) throws CoreException {
    if (utf8.length>65536) throw invalid();
    try {
      String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf8)).toString();
      StrictJson parser=new StrictJson(s);Object value=parser.value(0);parser.space();
      if (parser.pos!=s.length() || !(value instanceof Map)) throw invalid();
      @SuppressWarnings("unchecked") Map<String,Object> result=(Map<String,Object>)value;
      return result;
    } catch (CharacterCodingException|NumberFormatException|IndexOutOfBoundsException e) { throw invalid(); }
  }
  private static CoreException invalid() { return new CoreException(CoreException.Code.INVALID_PACKAGE,"Invalid bounded JSON metadata"); }
  private void space() { while(pos<text.length() && " \t\r\n".indexOf(text.charAt(pos))>=0)pos++; }
  private char take() { return text.charAt(pos++); }
  private Object value(int depth) throws CoreException {
    if(depth>8)throw invalid();space();char c=text.charAt(pos);
    if(c=='{') {
      pos++;Map<String,Object> map=new LinkedHashMap<>();space();if(text.charAt(pos)=='}'){pos++;return map;}
      do {space();if(text.charAt(pos)!='"')throw invalid();String key=string();space();if(take()!=':')throw invalid();if(map.containsKey(key)||map.size()>=128)throw invalid();map.put(key,value(depth+1));space();c=take();if(c=='}')return map;if(c!=',')throw invalid();}while(true);
    }
    if(c=='[') {
      pos++;List<Object> list=new ArrayList<>();space();if(text.charAt(pos)==']'){pos++;return list;}
      do {if(list.size()>=128)throw invalid();list.add(value(depth+1));space();c=take();if(c==']')return list;if(c!=',')throw invalid();}while(true);
    }
    if(c=='"')return string();
    for(String literal:new String[]{"true","false","null"})if(text.startsWith(literal,pos)){pos+=literal.length();return literal.equals("null")?null:Boolean.valueOf(literal);}
    int start=pos;if(c=='-')pos++;int digits=pos;
    while(pos<text.length() && text.charAt(pos)>='0' && text.charAt(pos)<='9')pos++;
    if(pos==digits || (pos-digits>1 && text.charAt(digits)=='0'))throw invalid();
    return Long.valueOf(text.substring(start,pos));
  }
  private String string() throws CoreException {
    if(take()!='"')throw invalid();StringBuilder b=new StringBuilder();
    while(true){char c=take();if(c=='"')break;if(c<32)throw invalid();if(c=='\\'){
      c=take();switch(c){case '"':case '\\':case '/':break;case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;case 'u':c=(char)Integer.parseInt(text.substring(pos,pos+4),16);pos+=4;break;default:throw invalid();}
    }b.append(c);if(b.length()>16384)throw invalid();}
    for(int i=0;i<b.length();i++)if(Character.isSurrogate(b.charAt(i))){if(!Character.isHighSurrogate(b.charAt(i))||i+1>=b.length()||!Character.isLowSurrogate(b.charAt(++i)))throw invalid();}
    return b.toString();
  }
}
