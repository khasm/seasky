package mie.crypto;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.MacSpi;
import javax.crypto.spec.SecretKeySpec;

public final class CBIRSparse extends MacSpi {
	
	private static final int INC = 50;
	
	private byte[] buffer;
	private List<String> words;
	private static PorterStemmer stem;
	private Mac mac;
	
	static{
		stem = new PorterStemmer();
	}
	
	public CBIRSparse(){
		mac = null;
	}

	@Override
	protected byte[] engineDoFinal() {
		processBuffer();
		return processWords();
	}

	@Override
	protected int engineGetMacLength() {
		return words.size()*20+9;
	}

	@Override
	protected void engineInit(Key key, AlgorithmParameterSpec params)
			throws InvalidKeyException, InvalidAlgorithmParameterException {
		if(key == null)
			throw new NullPointerException();
		CBIRSParameterSpec pspec;
		if(params == null){
			pspec = new CBIRSParameterSpec();
		}
		else if(params instanceof CBIRSParameterSpec){
			pspec = (CBIRSParameterSpec)params;
		}
		else{
			throw new InvalidAlgorithmParameterException("Wrong parameter spec");
		}
		if(!(key instanceof CBIRSKeySpec))
			throw new InvalidKeyException();
		try {
			try {
				mac = Mac.getInstance(pspec.getHmac(), pspec.getProvider());
			} catch (NoSuchProviderException | NoSuchAlgorithmException e) {
				mac = Mac.getInstance("HMAC_SHA1_ALGORITHM");
			}
			CBIRSKeySpec skey = (CBIRSKeySpec)key;
			Key sks = new SecretKeySpec(skey.getKey(), mac.getAlgorithm());
			mac.init(sks);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		buffer = new byte[0];
		words = new LinkedList<String>();

	}

	@Override
	protected void engineReset() {
		mac.reset();
		buffer = new byte[0];
		words = new LinkedList<String>();
	}

	@Override
	protected void engineUpdate(byte input) {
		byte[] buffer = new byte[1];
		buffer[0] = input;
		engineUpdate(buffer, 0, 1);
	}

	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		int buffer_count = buffer.length;
		byte[] tmp = new byte[buffer.length+len];
		for(int i = 0; i< buffer.length ; i++)
			tmp[i] = buffer[i];
		buffer = tmp;
		System.arraycopy(input, offset, buffer, buffer_count, len);
		processBuffer();
	}

	private byte[] processWords(){
		//long start = System.nanoTime(); ///start crypto benchmark
		TimeSpec.startCbirEncryptionTime();
		byte[] encoded = new byte[words.size()*20+9];
		ByteBuffer buf = ByteBuffer.wrap(encoded);
		buf.put(Utils.CBIRS);
		buf.putInt(words.size());
		buf.putInt(20);
		int i = buf.position();
		for(String s: words){
			byte[] enc = mac.doFinal(s.getBytes());
			for(byte b: enc)
				encoded[i++] = b;
			mac.reset();
		}
		words.clear();
		TimeSpec.addCbirEncryptionTime();
		/*long time = System.nanoTime()-start; ///end crypto benchmark
		TimeSpec.addCbirEncryptionTime(time);*/
		return encoded;
	}
	
	private void processBuffer(){
		//long start = System.nanoTime(); ///start feature extraction benchmark
		TimeSpec.startFeatureTime();
		int i = 0, keep = 0;
		int j;
		byte[] tmp_buffer = new byte[INC];
		while(true){
			char c;
			if(i < buffer.length)
				c = (char)buffer[i++];
			else
				break;
			if(Character.isLetterOrDigit(c)){
				j = 0;
				while(true){
					if(j == tmp_buffer.length){
						byte[] new_buffer = new byte[tmp_buffer.length+INC];
						for(int k = 0; k < tmp_buffer.length; k++)
							new_buffer[k] = tmp_buffer[k];
						tmp_buffer = new_buffer;
					}
					c = Character.toLowerCase(c);
					tmp_buffer[j++] = (byte)c;
					if(i < buffer.length)
						c = (char)buffer[i];
					else
						break;
					if(!Character.isLetterOrDigit(c)){
						break;
					}
					else{
						i++;
					}
				}
				String s = new String(tmp_buffer);
				if(!isStopword(s)){
					s = s.substring(0, stem.stem(tmp_buffer, 0, j-1)+1);
					words.add(s);
					keep = i;
				}
			}
		}
		if(keep != 0){
			int size = buffer.length-keep;
			byte[] b = new byte[size];
			for(int k = keep, bi = 0; k < buffer.length; k++, bi++){
				b[bi] = buffer[k];
			}
			buffer = b;
		}
		//long time = System.nanoTime()-start;///end feature extraction benchmark
		//TimeSpec.addFeatureTime(time);
		TimeSpec.addFeatureTime();
	}
	
	private boolean isStopword(String s){
		if(s.equals("a")||
			s.equals("able")||
			s.equals("about")||
			s.equals("above")||
			s.equals("according")||
			s.equals("accordingly")||
			s.equals("across")||
			s.equals("actually")||
			s.equals("after")||
			s.equals("afterwards")||
			s.equals("again")||
			s.equals("against")||
			s.equals("all")||
			s.equals("allow")||
			s.equals("allows")||
			s.equals("almost")||
			s.equals("alone")||
			s.equals("along")||
			s.equals("already")||
			s.equals("also")||
			s.equals("although")||
			s.equals("always")||
			s.equals("am")||
			s.equals("among")||
			s.equals("amongst")||
			s.equals("an")||
			s.equals("and")||
			s.equals("another")||
			s.equals("any")||
			s.equals("anybody")||
			s.equals("anyhow")||
			s.equals("anyone")||
			s.equals("anything")||
			s.equals("anyway")||
			s.equals("anyways")||
			s.equals("anywhere")||
			s.equals("apart")||
			s.equals("appear")||
			s.equals("appreciate")||
			s.equals("appropriate")||
			s.equals("are")||
			s.equals("around")||
			s.equals("as")||
			s.equals("aside")||
			s.equals("ask")||
			s.equals("asking")||
			s.equals("associated")||
			s.equals("at")||
			s.equals("available")||
			s.equals("away")||
			s.equals("awfully")||
			s.equals("b")||
			s.equals("be")||
			s.equals("became")||
			s.equals("because")||
			s.equals("become")||
			s.equals("becomes")||
			s.equals("becoming")||
			s.equals("been")||
			s.equals("before")||
			s.equals("beforehand")||
			s.equals("behind")||
			s.equals("being")||
			s.equals("believe")||
			s.equals("below")||
			s.equals("beside")||
			s.equals("besides")||
			s.equals("best")||
			s.equals("better")||
			s.equals("between")||
			s.equals("beyond")||
			s.equals("both")||
			s.equals("brief")||
			s.equals("but")||
			s.equals("by")||
			s.equals("c")||
			s.equals("came")||
			s.equals("can")||
			s.equals("cannot")||
			s.equals("cant")||
			s.equals("cause")||
			s.equals("causes")||
			s.equals("certain")||
			s.equals("certainly")||
			s.equals("changes")||
			s.equals("clearly")||
			s.equals("co")||
			s.equals("com")||
			s.equals("come")||
			s.equals("comes")||
			s.equals("concerning")||
			s.equals("consequently")||
			s.equals("consider")||
			s.equals("considering")||
			s.equals("contain")||
			s.equals("containing")||
			s.equals("contains")||
			s.equals("corresponding")||
			s.equals("could")||
			s.equals("course")||
			s.equals("currently")||
			s.equals("d")||
			s.equals("definitely")||
			s.equals("described")||
			s.equals("despite")||
			s.equals("did")||
			s.equals("different")||
			s.equals("do")||
			s.equals("does")||
			s.equals("doing")||
			s.equals("done")||
			s.equals("down")||
			s.equals("downwards")||
			s.equals("during")||
			s.equals("e")||
			s.equals("each")||
			s.equals("edu")||
			s.equals("eg")||
			s.equals("eight")||
			s.equals("either")||
			s.equals("else")||
			s.equals("elsewhere")||
			s.equals("enough")||
			s.equals("entirely")||
			s.equals("especially")||
			s.equals("et")||
			s.equals("etc")||
			s.equals("even")||
			s.equals("ever")||
			s.equals("every")||
			s.equals("everybody")||
			s.equals("everyone")||
			s.equals("everything")||
			s.equals("everywhere")||
			s.equals("ex")||
			s.equals("exactly")||
			s.equals("example")||
			s.equals("except")||
			s.equals("f")||
			s.equals("far")||
			s.equals("few")||
			s.equals("fifth")||
			s.equals("first")||
			s.equals("five")||
			s.equals("followed")||
			s.equals("following")||
			s.equals("follows")||
			s.equals("for")||
			s.equals("former")||
			s.equals("formerly")||
			s.equals("forth")||
			s.equals("four")||
			s.equals("from")||
			s.equals("further")||
			s.equals("furthermore")||
			s.equals("g")||
			s.equals("get")||
			s.equals("gets")||
			s.equals("getting")||
			s.equals("given")||
			s.equals("gives")||
			s.equals("go")||
			s.equals("goes")||
			s.equals("going")||
			s.equals("gone")||
			s.equals("got")||
			s.equals("gotten")||
			s.equals("greetings")||
			s.equals("h")||
			s.equals("had")||
			s.equals("happens")||
			s.equals("hardly")||
			s.equals("has")||
			s.equals("have")||
			s.equals("having")||
			s.equals("he")||
			s.equals("hello")||
			s.equals("help")||
			s.equals("hence")||
			s.equals("her")||
			s.equals("here")||
			s.equals("hereafter")||
			s.equals("hereby")||
			s.equals("herein")||
			s.equals("hereupon")||
			s.equals("hers")||
			s.equals("herself")||
			s.equals("hi")||
			s.equals("him")||
			s.equals("himself")||
			s.equals("his")||
			s.equals("hither")||
			s.equals("hopefully")||
			s.equals("how")||
			s.equals("howbeit")||
			s.equals("however")||
			s.equals("i")||
			s.equals("ie")||
			s.equals("if")||
			s.equals("ignored")||
			s.equals("immediate")||
			s.equals("in")||
			s.equals("inasmuch")||
			s.equals("inc")||
			s.equals("indeed")||
			s.equals("indicate")||
			s.equals("indicated")||
			s.equals("indicates")||
			s.equals("inner")||
			s.equals("insofar")||
			s.equals("instead")||
			s.equals("into")||
			s.equals("inward")||
			s.equals("is")||
			s.equals("it")||
			s.equals("its")||
			s.equals("itself")||
			s.equals("j")||
			s.equals("just")||
			s.equals("k")||
			s.equals("keep")||
			s.equals("keeps")||
			s.equals("kept")||
			s.equals("know")||
			s.equals("knows")||
			s.equals("known")||
			s.equals("l")||
			s.equals("last")||
			s.equals("lately")||
			s.equals("later")||
			s.equals("latter")||
			s.equals("latterly")||
			s.equals("least")||
			s.equals("less")||
			s.equals("lest")||
			s.equals("let")||
			s.equals("like")||
			s.equals("liked")||
			s.equals("likely")||
			s.equals("little")||
			s.equals("ll")|| // added to avoid words like you'll,I'll etc.
			s.equals("look")||
			s.equals("looking")||
			s.equals("looks")||
			s.equals("ltd")||
			s.equals("m")||
			s.equals("mainly")||
			s.equals("many")||
			s.equals("may")||
			s.equals("maybe")||
			s.equals("me")||
			s.equals("mean")||
			s.equals("meanwhile")||
			s.equals("merely")||
			s.equals("might")||
			s.equals("more")||
			s.equals("moreover")||
			s.equals("most")||
			s.equals("mostly")||
			s.equals("much")||
			s.equals("must")||
			s.equals("my")||
			s.equals("myself")||
			s.equals("n")||
			s.equals("name")||
			s.equals("namely")||
			s.equals("nd")||
			s.equals("near")||
			s.equals("nearly")||
			s.equals("necessary")||
			s.equals("need")||
			s.equals("needs")||
			s.equals("neither")||
			s.equals("never")||
			s.equals("nevertheless")||
			s.equals("new")||
			s.equals("next")||
			s.equals("nine")||
			s.equals("no")||
			s.equals("nobody")||
			s.equals("non")||
			s.equals("none")||
			s.equals("noone")||
			s.equals("nor")||
			s.equals("normally")||
			s.equals("not")||
			s.equals("nothing")||
			s.equals("novel")||
			s.equals("now")||
			s.equals("nowhere")||
			s.equals("o")||
			s.equals("obviously")||
			s.equals("of")||
			s.equals("off")||
			s.equals("often")||
			s.equals("oh")||
			s.equals("ok")||
			s.equals("okay")||
			s.equals("old")||
			s.equals("on")||
			s.equals("once")||
			s.equals("one")||
			s.equals("ones")||
			s.equals("only")||
			s.equals("onto")||
			s.equals("or")||
			s.equals("other")||
			s.equals("others")||
			s.equals("otherwise")||
			s.equals("ought")||
			s.equals("our")||
			s.equals("ours")||
			s.equals("ourselves")||
			s.equals("out")||
			s.equals("outside")||
			s.equals("over")||
			s.equals("overall")||
			s.equals("own")||
			s.equals("p")||
			s.equals("particular")||
			s.equals("particularly")||
			s.equals("per")||
			s.equals("perhaps")||
			s.equals("placed")||
			s.equals("please")||
			s.equals("plus")||
			s.equals("possible")||
			s.equals("presumably")||
			s.equals("probably")||
			s.equals("provides")||
			s.equals("q")||
			s.equals("que")||
			s.equals("quite")||
			s.equals("qv")||
			s.equals("r")||
			s.equals("rather")||
			s.equals("rd")||
			s.equals("re")||
			s.equals("really")||
			s.equals("reasonably")||
			s.equals("regarding")||
			s.equals("regardless")||
			s.equals("regards")||
			s.equals("relatively")||
			s.equals("respectively")||
			s.equals("right")||
			s.equals("s")||
			s.equals("said")||
			s.equals("same")||
			s.equals("saw")||
			s.equals("say")||
			s.equals("saying")||
			s.equals("says")||
			s.equals("second")||
			s.equals("secondly")||
			s.equals("see")||
			s.equals("seeing")||
			s.equals("seem")||
			s.equals("seemed")||
			s.equals("seeming")||
			s.equals("seems")||
			s.equals("seen")||
			s.equals("self")||
			s.equals("selves")||
			s.equals("sensible")||
			s.equals("sent")||
			s.equals("serious")||
			s.equals("seriously")||
			s.equals("seven")||
			s.equals("several")||
			s.equals("shall")||
			s.equals("she")||
			s.equals("should")||
			s.equals("since")||
			s.equals("six")||
			s.equals("so")||
			s.equals("some")||
			s.equals("somebody")||
			s.equals("somehow")||
			s.equals("someone")||
			s.equals("something")||
			s.equals("sometime")||
			s.equals("sometimes")||
			s.equals("somewhat")||
			s.equals("somewhere")||
			s.equals("soon")||
			s.equals("sorry")||
			s.equals("specified")||
			s.equals("specify")||
			s.equals("specifying")||
			s.equals("still")||
			s.equals("sub")||
			s.equals("such")||
			s.equals("sup")||
			s.equals("sure")||
			s.equals("t")||
			s.equals("take")||
			s.equals("taken")||
			s.equals("tell")||
			s.equals("tends")||
			s.equals("th")||
			s.equals("than")||
			s.equals("thank")||
			s.equals("thanks")||
			s.equals("thanx")||
			s.equals("that")||
			s.equals("thats")||
			s.equals("the")||
			s.equals("their")||
			s.equals("theirs")||
			s.equals("them")||
			s.equals("themselves")||
			s.equals("then")||
			s.equals("thence")||
			s.equals("there")||
			s.equals("thereafter")||
			s.equals("thereby")||
			s.equals("therefore")||
			s.equals("therein")||
			s.equals("theres")||
			s.equals("thereupon")||
			s.equals("these")||
			s.equals("they")||
			s.equals("think")||
			s.equals("third")||
			s.equals("this")||
			s.equals("thorough")||
			s.equals("thoroughly")||
			s.equals("those")||
			s.equals("though")||
			s.equals("three")||
			s.equals("through")||
			s.equals("throughout")||
			s.equals("thru")||
			s.equals("thus")||
			s.equals("to")||
			s.equals("together")||
			s.equals("too")||
			s.equals("took")||
			s.equals("toward")||
			s.equals("towards")||
			s.equals("tried")||
			s.equals("tries")||
			s.equals("truly")||
			s.equals("try")||
			s.equals("trying")||
			s.equals("twice")||
			s.equals("two")||
			s.equals("u")||
			s.equals("un")||
			s.equals("under")||
			s.equals("unfortunately")||
			s.equals("unless")||
			s.equals("unlikely")||
			s.equals("until")||
			s.equals("unto")||
			s.equals("up")||
			s.equals("upon")||
			s.equals("us")||
			s.equals("use")||
			s.equals("used")||
			s.equals("useful")||
			s.equals("uses")||
			s.equals("using")||
			s.equals("usually")||
			s.equals("uucp")||
			s.equals("v")||
			s.equals("value")||
			s.equals("various")||
			s.equals("ve")|| // added to avoid words like I've,you've etc.
			s.equals("very")||
			s.equals("via")||
			s.equals("viz")||
			s.equals("vs")||
			s.equals("w")||
			s.equals("want")||
			s.equals("wants")||
			s.equals("was")||
			s.equals("way")||
			s.equals("we")||
			s.equals("welcome")||
			s.equals("well")||
			s.equals("went")||
			s.equals("were")||
			s.equals("what")||
			s.equals("whatever")||
			s.equals("when")||
			s.equals("whence")||
			s.equals("whenever")||
			s.equals("where")||
			s.equals("whereafter")||
			s.equals("whereas")||
			s.equals("whereby")||
			s.equals("wherein")||
			s.equals("whereupon")||
			s.equals("wherever")||
			s.equals("whether")||
			s.equals("which")||
			s.equals("while")||
			s.equals("whither")||
			s.equals("who")||
			s.equals("whoever")||
			s.equals("whole")||
			s.equals("whom")||
			s.equals("whose")||
			s.equals("why")||
			s.equals("will")||
			s.equals("willing")||
			s.equals("wish")||
			s.equals("with")||
			s.equals("within")||
			s.equals("without")||
			s.equals("wonder")||
			s.equals("would")||
			s.equals("would")||
			s.equals("x")||
			s.equals("y")||
			s.equals("yes")||
			s.equals("yet")||
			s.equals("you")||
			s.equals("your")||
			s.equals("yours")||
			s.equals("yourself")||
			s.equals("yourselves")||
			s.equals("z")||
			s.equals("zero"))
			return true;
		else
			return false;
	}
}
